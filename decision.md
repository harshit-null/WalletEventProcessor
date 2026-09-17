# Architectural Decisions

## 1. Concurrency & Race Condition Handling

### Problem Statement
In a wallet transaction system with concurrent requests, we needed to prevent:
- **Lost Updates**: Multiple concurrent transactions modifying the same wallet balance
- **Double-Spending**: Same transaction ID processed multiple times (idempotency violation)
- **Negative Balances**: Multiple concurrent debits exceeding available balance

### Solution: Pessimistic Locking + Idempotency Check

#### 1.1 Pessimistic Locking Strategy

**Implementation**:
```java
// WalletRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
Optional<Wallet> findByUserIdWithLock(UUID userId);

// PaymentServiceImpl.java
Wallet wallet = walletRepository
    .findByUserIdWithLock(request.userId())
    .orElseThrow(() -> new WalletNotFoundException(...));
```

**How It Works**:
- `LockModeType.PESSIMISTIC_WRITE` acquires an exclusive database lock on the wallet row
- Lock is held for the duration of the transaction
- All other concurrent threads trying to access the same wallet must **wait** for the lock to be released
- Ensures only one thread can modify a wallet at a time → **no race conditions**

#### 1.2 Idempotency Check (After Lock Acquired)

**Key Insight**: Check idempotency **AFTER** acquiring the lock, not before.

**Why?**:
```
WRONG APPROACH (Race Condition):
├─ Thread 1: existsByIdempotencyKey("txn-123") → false
├─ Thread 2: existsByIdempotencyKey("txn-123") → false
├─ Thread 1: Acquire lock → Process transaction → Save event
├─ Thread 2: Acquire lock → Process transaction → Duplicate save (RACE!)

CORRECT APPROACH (Safe):
├─ Thread 1: Acquire lock on wallet
├─ Thread 2: Wait for lock
├─ Thread 1: existsByIdempotencyKey("txn-123") → false → Process
├─ Thread 1: Save event with transactionId
├─ Thread 1: Release lock
├─ Thread 2: Acquire lock on wallet
├─ Thread 2: existsByIdempotencyKey("txn-123") → true → Throw DuplicateTransactionException
```

**Implementation Order** (Critical):
```java
1. Lock wallet: findByUserIdWithLock() // MUST be first
2. Check idempotency: existsByIdempotencyKey() // After lock
3. Process transaction (debit/credit)
4. Save wallet & event
5. Release lock (auto on transaction commit)
```

#### 1.3 Test Coverage for Concurrency

**Test 1: Idempotency with 3 Concurrent Identical Transactions**
```
Initial Balance: ₹1000
Transaction: DEBIT ₹100 with transactionId = "abc123"

3 concurrent threads, all with same transactionId:
├─ Thread 1: SUCCESS (lock acquired, idempotency check passes, deducts ₹100)
├─ Thread 2: CONFLICT 409 (waiting for lock, then idempotency check fails)
├─ Thread 3: CONFLICT 409 (waiting for lock, then idempotency check fails)

Final Balance: ₹900 (deducted only once) ✅
Events Created: 1 (not 3) ✅
```

**Test 2: Concurrency with 10 Debits on ₹500 Wallet**
```
Initial Balance: ₹500
Transaction: DEBIT ₹100 × 10 concurrent threads (each with unique transactionId)

10 concurrent threads:
├─ Threads 1-5: SUCCESS (₹100 each, total ₹500 deducted)
├─ Threads 6-10: INSUFFICIENT_BALANCE (no funds remaining)

Final Balance: ₹0 (exact, not negative) ✅
Successful Transactions: 5 ✅
Failed Transactions: 5 ✅
```

---

## 2. Incorrect or Suboptimal Answers

### Mistake 1: Initial Optimistic Locking in Wallet Entity

**What AI Did**:
Initially added a `@Version` field for optimistic locking:
```java
@Version
private Long version;
```

**Why It Was Wrong**:
- Optimistic locking uses version numbers and `StaleObjectStateException`
- Doesn't **prevent** concurrent modifications; detects them **after** they happen
- Multiple threads can still read stale data and overwrite each other's changes
- For financial systems, this is **unsafe** - we need **preventing** concurrency, not detecting it

**Correct Solution** (What We Implemented):
- Removed `@Version` field
- Used `@Lock(LockModeType.PESSIMISTIC_WRITE)` instead
- Pessimistic locking **prevents** concurrent access, not just detects it

**Why Pessimistic is Better for Finance**:
```
Optimistic (Wrong):
Thread 1 reads balance ₹100
Thread 2 reads balance ₹100
Thread 1 deducts ₹60 → balance ₹40
Thread 2 deducts ₹70 → balance ₹30 (WRONG! Should have failed)

Pessimistic (Correct):
Thread 1 locks wallet, reads balance ₹100
Thread 2 waits for lock
Thread 1 deducts ₹60 → balance ₹40, releases lock
Thread 2 acquires lock, reads balance ₹40
Thread 2 tries to deduct ₹70 → INSUFFICIENT_BALANCE exception ✅
```

---

### Mistake 2: Initial Order of Idempotency Check

**What AI Said Initially**:
"Check if transaction already exists BEFORE acquiring the lock to fail fast"

**Why It Was Wrong**:
- As documented in section 1.2 above, this creates a race condition window
- Even if lock acquisition is "fast," the gap between check and lock is enough for race conditions
- "Fail fast" principle conflicts with correctness in concurrent systems

**Correct Order**:
1. Acquire lock FIRST
2. Then check idempotency
3. Then process

**Learning**: In concurrent systems, **correctness > performance**. We must accept the overhead of acquiring locks before checks.

---

### ⚠️ Mistake 3: H2 Console Configuration Issue

**What I Tried**:
Multiple approaches to get H2 console working at `/h2-console`:
1. Added properties like `spring.h2.console.settings.web-allow-others`
2. Tried changing path to `/h2`
3. Attempted to create custom `ServletRegistrationBean` with `WebServlet`

**Why It Didn't Work**:
- Spent significant time on a **compatibility issue** between:
    - H2 library using `javax.servlet.*` (Java EE)
    - Spring Boot 6.x using `jakarta.servlet.*` (Jakarta EE)
- These are fundamentally incompatible; no configuration can fix it
- Should have **recognized the root cause faster** and pivoted to alternatives

**What I Should Have Done Differently**:
- Immediately documented the issue as known limitation
- Provided alternative solutions (standalone H2 console, DBeaver, IntelliJ) right away
- Spent less time debugging configurations, more time on workarounds

**Time Cost**: ~30 minutes of unproductive debugging

---

### ⚠️ Mistake 4: GlobalExceptionHandler Initially Incompatible with Service Changes

**What Happened**:
1. Created `GlobalExceptionHandler` that catches custom exceptions like `DuplicateTransactionException`
2. Updated service to throw `DuplicateTransactionException`
3. Tests were still catching generic `IllegalStateException`
4. Tests failed until exception types were synchronized

**Why This Was Suboptimal**:
- Should have updated test exception handlers **in the same commit** as service changes
- Better approach: Test-Driven Development (TDD)
    - Write tests first with expected exception types
    - Then implement service with matching exceptions
    - Then create global exception handler

**Lesson Learned**:
- When refactoring exception hierarchy, update all layers simultaneously
- Or use TDD to drive the implementation from tests

---

### ⚠️ Mistake 5: Lombok Configuration Debugging

**What Happened**:
- Spent time wondering why IntelliJ wasn't recognizing Lombok-generated methods (`getBalance()`, `builder()`)
- The Maven build worked fine; only IDE had issues
- Eventually fixed by adding version to annotation processor paths

**What I Should Have Done**:
- Recognized the pattern faster: "Maven builds but IDE doesn't recognize" = IDE plugin configuration
- Mentioned from the start: "Run `File → Invalidate Caches → Restart` in IntelliJ" (standard fix)
- Provided pom.xml fix as the primary solution instead of trial-and-error

**Better Approach**:
1. Diagnose: Check Maven compilation (✅ worked)
2. Diagnose: Check IDE recognition (❌ failed)
3. Identify: IDE needs annotation processor paths configured
4. Fix: Add `<version>` to annotation processor in pom.xml
5. Workaround: Invalidate caches and restart
