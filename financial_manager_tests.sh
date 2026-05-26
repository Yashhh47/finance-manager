#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status (not wanted here, we want to continue tests)
# set -e

# Default Base URL
BASE_URL="${1:-http://localhost:8080/api}"

# Trim trailing slash if present
BASE_URL="${BASE_URL%/}"

echo "=========================================================="
echo "   Personal Finance Manager API - Integration Test Suite"
echo "   Target URL: $BASE_URL"
echo "=========================================================="

# Statistics
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Temporary cookie storage
COOKIE_ALICE="/tmp/cookie_alice.txt"
COOKIE_BOB="/tmp/cookie_bob.txt"

# Cleanup cookies on exit
cleanup() {
    rm -f "$COOKIE_ALICE" "$COOKIE_BOB" "/tmp/test_response.json"
}
trap cleanup EXIT

# Clear old cookies
cleanup

# Helper function to assert HTTP status and body content
# Arguments:
#   1. Test Name
#   2. Expected HTTP Code
#   3. Actual HTTP Code
#   4. Expected Substring in Body (optional)
#   5. Actual Body Content
assert_test() {
    local test_name="$1"
    local expected_code="$2"
    local actual_code="$3"
    local expected_body_match="$4"
    local actual_body="$5"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    if [ "$actual_code" != "$expected_code" ]; then
        echo -e "\033[0;31m[FAIL]\033[0;0m $test_name (Expected HTTP $expected_code, got $actual_code)"
        echo "       Body: $actual_body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi

    if [ -n "$expected_body_match" ]; then
        if ! echo "$actual_body" | grep -q "$expected_body_match"; then
            echo -e "\033[0;31m[FAIL]\033[0;0m $test_name (Expected body to contain '$expected_body_match')"
            echo "       Body: $actual_body"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            return 1
        fi
    fi

    echo -e "\033[0;32m[PASS]\033[0;0m $test_name"
    PASSED_TESTS=$((PASSED_TESTS + 1))
    return 0
}

# Helper to run curl requests
# Usage: run_request <method> <path> <cookie_file_or_empty> <data_or_empty>
run_request() {
    local method="$1"
    local path="$2"
    local cookie_file="$3"
    local data="$4"

    local curl_cmd=(curl -s -w "\n%{http_code}" -X "$method")

    # Add header
    curl_cmd+=(-H "Content-Type: application/json")

    # Add cookie file configuration
    if [ -n "$cookie_file" ]; then
        # Create cookie file if it doesn't exist to store cookies
        touch "$cookie_file"
        curl_cmd+=(-b "$cookie_file" -c "$cookie_file")
    fi

    # Add JSON data if present
    if [ -n "$data" ]; then
        curl_cmd+=(-d "$data")
    fi

    # Append request URL
    curl_cmd+=("$BASE_URL$path")

    # Execute and capture response
    local response
    response=$( "${curl_cmd[@]}" )
    
    # Parse body and status code
    local body
    body=$(echo "$response" | sed '$d')
    local status
    status=$(echo "$response" | tail -n 1)

    # Export results for caller
    LAST_BODY="$body"
    LAST_STATUS="$status"
}

# Extracts a value from JSON using python's json tool
extract_json_val() {
    local json="$1"
    local key="$2"
    echo "$json" | python3 -c "import sys, json; print(json.load(sys.stdin).get('$key', ''))" 2>/dev/null
}


echo -e "\n----------------------------------------------------------"
echo " 1. AUTHENTICATION & USER MANAGEMENT TESTS"
echo "----------------------------------------------------------"

# Test 1.1: Register Alice
run_request "POST" "/auth/register" "" '{"username":"alice@test.com","password":"password123","fullName":"Alice Smith","phoneNumber":"1234567890"}'
assert_test "Register Alice (Success 201)" "201" "$LAST_STATUS" "User registered successfully" "$LAST_BODY"
ALICE_ID=$(extract_json_val "$LAST_BODY" "userId")

# Test 1.2: Register Bob
run_request "POST" "/auth/register" "" '{"username":"bob@test.com","password":"password456","fullName":"Bob Jones","phoneNumber":"0987654321"}'
assert_test "Register Bob (Success 201)" "201" "$LAST_STATUS" "User registered successfully" "$LAST_BODY"
BOB_ID=$(extract_json_val "$LAST_BODY" "userId")

# Test 1.3: Duplicate registration check
run_request "POST" "/auth/register" "" '{"username":"alice@test.com","password":"password123","fullName":"Alice Smith","phoneNumber":"1234567890"}'
assert_test "Register Alice again (Conflict 409)" "409" "$LAST_STATUS" "Username already exists" "$LAST_BODY"

# Test 1.4: Registration validation - Invalid Email Format
run_request "POST" "/auth/register" "" '{"username":"not-an-email","password":"password123","fullName":"Alice Smith","phoneNumber":"1234567890"}'
assert_test "Register with invalid email format (Bad Request 400)" "400" "$LAST_STATUS" "username:" "$LAST_BODY"

# Test 1.5: Registration validation - Short Password
run_request "POST" "/auth/register" "" '{"username":"shortpwd@test.com","password":"123","fullName":"Short Pwd","phoneNumber":"1234567890"}'
assert_test "Register with short password (Bad Request 400)" "400" "$LAST_STATUS" "password:" "$LAST_BODY"

# Test 1.6: Registration validation - Missing Required Full Name
run_request "POST" "/auth/register" "" '{"username":"missingname@test.com","password":"password123","phoneNumber":"1234567890"}'
assert_test "Register with missing full name (Bad Request 400)" "400" "$LAST_STATUS" "fullName:" "$LAST_BODY"

# Test 1.7: Alice Login - Wrong Password
run_request "POST" "/auth/login" "" '{"username":"alice@test.com","password":"wrongpassword"}'
assert_test "Alice Login - Wrong Password (Unauthorized 401)" "401" "$LAST_STATUS" "" "$LAST_BODY"

# Test 1.8: Alice Login - Non-existent User
run_request "POST" "/auth/login" "" '{"username":"ghost@test.com","password":"password123"}'
assert_test "Alice Login - Non-existent User (Unauthorized 401)" "401" "$LAST_STATUS" "" "$LAST_BODY"

# Test 1.9: Alice Login - Success
run_request "POST" "/auth/login" "$COOKIE_ALICE" '{"username":"alice@test.com","password":"password123"}'
assert_test "Alice Login (Success 200)" "200" "$LAST_STATUS" "Login successful" "$LAST_BODY"

# Test 1.10: Bob Login - Success
run_request "POST" "/auth/login" "$COOKIE_BOB" '{"username":"bob@test.com","password":"password456"}'
assert_test "Bob Login (Success 200)" "200" "$LAST_STATUS" "Login successful" "$LAST_BODY"

# Test 1.11: Access protected resource without logging in (using empty cookie session)
run_request "GET" "/categories" "" ""
assert_test "Access categories unauthenticated (Unauthorized 401)" "401" "$LAST_STATUS" "Unauthorized" "$LAST_BODY"


echo -e "\n----------------------------------------------------------"
echo " 2. CATEGORY CONFIGURATIONS & SEEDING TESTS"
echo "----------------------------------------------------------"

# Test 2.1: Get all categories for Alice (should contain seeded system ones)
run_request "GET" "/categories" "$COOKIE_ALICE" ""
assert_test "Get Alice's categories (Success 200)" "200" "$LAST_STATUS" "Salary" "$LAST_BODY"
assert_test "Check system category: Food" "200" "$LAST_STATUS" "Food" "$LAST_BODY"
assert_test "Check system category: Rent" "200" "$LAST_STATUS" "Rent" "$LAST_BODY"
assert_test "Check system category: Transportation" "200" "$LAST_STATUS" "Transportation" "$LAST_BODY"
assert_test "Check system category: Entertainment" "200" "$LAST_STATUS" "Entertainment" "$LAST_BODY"
assert_test "Check system category: Healthcare" "200" "$LAST_STATUS" "Healthcare" "$LAST_BODY"
assert_test "Check system category: Utilities" "200" "$LAST_STATUS" "Utilities" "$LAST_BODY"

# Test 2.2: Create Alice's Custom Category
run_request "POST" "/categories" "$COOKIE_ALICE" '{"name":"Freelance","type":"INCOME"}'
assert_test "Create custom category 'Freelance' for Alice (Created 201)" "201" "$LAST_STATUS" "Freelance" "$LAST_BODY"

# Test 2.3: Create duplicate custom category for Alice
run_request "POST" "/categories" "$COOKIE_ALICE" '{"name":"Freelance","type":"INCOME"}'
assert_test "Create duplicate custom category (Conflict 409)" "409" "$LAST_STATUS" "already exists" "$LAST_BODY"

# Test 2.4: Create custom category matching system category name
run_request "POST" "/categories" "$COOKIE_ALICE" '{"name":"Food","type":"EXPENSE"}'
assert_test "Create custom category matching system name (Conflict 409)" "409" "$LAST_STATUS" "already exists" "$LAST_BODY"

# Test 2.5: Category Creation Validation - Bad Type
run_request "POST" "/categories" "$COOKIE_ALICE" '{"name":"SideHustle","type":"NOT_INCOME_OR_EXPENSE"}'
assert_test "Create custom category with invalid type (Bad Request 400)" "400" "$LAST_STATUS" "" "$LAST_BODY"

# Test 2.6: Verify Bob cannot see Alice's custom category
run_request "GET" "/categories" "$COOKIE_BOB" ""
assert_test "Get Bob's categories (Success 200)" "200" "$LAST_STATUS" "Salary" "$LAST_BODY"
# Freelance should NOT be in Bob's category list
if echo "$LAST_BODY" | grep -q "Freelance"; then
    echo -e "\033[0;31m[FAIL]\033[0;0m Data Isolation: Bob can see Alice's custom category Freelance!"
    FAILED_TESTS=$((FAILED_TESTS + 1))
else
    echo -e "\033[0;32m[PASS]\033[0;0m Data Isolation: Bob cannot see Alice's custom category Freelance"
    PASSED_TESTS=$((PASSED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 2.7: Try to delete system category (Food)
run_request "DELETE" "/categories/Food" "$COOKIE_ALICE" ""
assert_test "Delete system default category (Forbidden 403)" "403" "$LAST_STATUS" "system default" "$LAST_BODY"

# Test 2.8: Try to delete non-existent category
run_request "DELETE" "/categories/NonExistent" "$COOKIE_ALICE" ""
assert_test "Delete non-existent category (Not Found 404)" "404" "$LAST_STATUS" "not found" "$LAST_BODY"

# Test 2.9: Delete Alice's custom category (Freelance)
# We will do this after verifying transaction safety below.


echo -e "\n----------------------------------------------------------"
echo " 3. TRANSACTION MANAGEMENT & DATA ISOLATION TESTS"
echo "----------------------------------------------------------"

# Test 3.1: Alice creates an INCOME transaction using seeded Category
run_request "POST" "/transactions" "$COOKIE_ALICE" '{"amount":5000.00,"date":"2026-05-01","categoryName":"Salary","description":"Monthly Main Job Salary"}'
assert_test "Alice creates income transaction (Created 201)" "201" "$LAST_STATUS" "Salary" "$LAST_BODY"
TXN_ALICE_INC_ID=$(extract_json_val "$LAST_BODY" "id")

# Test 3.2: Alice creates an EXPENSE transaction using seeded Category
run_request "POST" "/transactions" "$COOKIE_ALICE" '{"amount":1200.00,"date":"2026-05-05","categoryName":"Rent","description":"Apartment rent payment"}'
assert_test "Alice creates expense transaction (Created 201)" "201" "$LAST_STATUS" "Rent" "$LAST_BODY"
TXN_ALICE_EXP_ID=$(extract_json_val "$LAST_BODY" "id")

# Test 3.3: Alice creates an INCOME transaction using Custom Category
run_request "POST" "/transactions" "$COOKIE_ALICE" '{"amount":450.00,"date":"2026-05-10","categoryName":"Freelance","description":"Web Design Gig"}'
assert_test "Alice creates custom category income txn (Created 201)" "201" "$LAST_STATUS" "Freelance" "$LAST_BODY"
TXN_ALICE_CUST_ID=$(extract_json_val "$LAST_BODY" "id")

# Test 3.4: Try to delete Custom Category 'Freelance' while in use
run_request "DELETE" "/categories/Freelance" "$COOKIE_ALICE" ""
assert_test "Delete linked category Freelance (Bad Request 400)" "400" "$LAST_STATUS" "linked to existing transactions" "$LAST_BODY"

# Test 3.5: Transaction Validation - Future Date
run_request "POST" "/transactions" "$COOKIE_ALICE" '{"amount":100.00,"date":"2030-01-01","categoryName":"Food","description":"Future Lunch"}'
assert_test "Create transaction in future (Bad Request 400)" "400" "$LAST_STATUS" "cannot be in the future" "$LAST_BODY"

# Test 3.6: Transaction Validation - Negative Amount
run_request "POST" "/transactions" "$COOKIE_ALICE" '{"amount":-50.00,"date":"2026-05-02","categoryName":"Food","description":"Invalid negative amount"}'
assert_test "Create transaction with negative amount (Bad Request 400)" "400" "$LAST_STATUS" "amount:" "$LAST_BODY"

# Test 3.7: Transaction Validation - Missing Amount
run_request "POST" "/transactions" "$COOKIE_ALICE" '{"date":"2026-05-02","categoryName":"Food","description":"No Amount"}'
assert_test "Create transaction with missing amount (Bad Request 400)" "400" "$LAST_STATUS" "amount:" "$LAST_BODY"

# Test 3.8: Transaction Validation - Non-existent Category
run_request "POST" "/transactions" "$COOKIE_ALICE" '{"amount":100.00,"date":"2026-05-02","categoryName":"ImaginaryCategory","description":"Ghost"}'
assert_test "Create transaction with invalid category name (Bad Request 400)" "400" "$LAST_STATUS" "not found" "$LAST_BODY"

# Test 3.9: Bob creates a transaction
run_request "POST" "/transactions" "$COOKIE_BOB" '{"amount":8000.00,"date":"2026-05-01","categoryName":"Salary","description":"Bob Monthly Salary"}'
assert_test "Bob creates income transaction (Created 201)" "201" "$LAST_STATUS" "Salary" "$LAST_BODY"
TXN_BOB_ID=$(extract_json_val "$LAST_BODY" "id")

# Test 3.10: Data Isolation - Alice tries to GET Bob's transaction through filtering
# (The API returns only the logged-in user's transactions)
run_request "GET" "/transactions" "$COOKIE_ALICE" ""
assert_test "Alice fetches all transactions (Success 200)" "200" "$LAST_STATUS" "transactions" "$LAST_BODY"
if echo "$LAST_BODY" | grep -q "Bob Monthly Salary"; then
    echo -e "\033[0;31m[FAIL]\033[0;0m Data Isolation: Alice can view Bob's transaction!"
    FAILED_TESTS=$((FAILED_TESTS + 1))
else
    echo -e "\033[0;32m[PASS]\033[0;0m Data Isolation: Alice cannot view Bob's transaction"
    PASSED_TESTS=$((PASSED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 3.11: Data Isolation - Alice tries to update Bob's transaction
run_request "PUT" "/transactions/$TXN_BOB_ID" "$COOKIE_ALICE" '{"amount":1.00,"categoryName":"Salary"}'
assert_test "Alice updates Bob's transaction (Forbidden 403)" "403" "$LAST_STATUS" "Access denied" "$LAST_BODY"

# Test 3.12: Data Isolation - Alice tries to delete Bob's transaction
run_request "DELETE" "/transactions/$TXN_BOB_ID" "$COOKIE_ALICE" ""
assert_test "Alice deletes Bob's transaction (Forbidden 403)" "403" "$LAST_STATUS" "Access denied" "$LAST_BODY"

# Test 3.13: Filter Transactions by Date Range
run_request "GET" "/transactions?startDate=2026-05-01&endDate=2026-05-05" "$COOKIE_ALICE" ""
assert_test "Get transactions filtered by date range (Success 200)" "200" "$LAST_STATUS" "transactions" "$LAST_BODY"
assert_test "Verify Salary txn in filtered list" "200" "$LAST_STATUS" "Monthly Main Job Salary" "$LAST_BODY"
assert_test "Verify Rent txn in filtered list" "200" "$LAST_STATUS" "Apartment rent payment" "$LAST_BODY"
# The freelance txn from May 10 should not be present
if echo "$LAST_BODY" | grep -q "Web Design Gig"; then
    echo -e "\033[0;31m[FAIL]\033[0;0m Filter test failed: future transaction included"
    FAILED_TESTS=$((FAILED_TESTS + 1))
else
    echo -e "\033[0;32m[PASS]\033[0;0m Filter test correct: excludes transaction out of date range"
    PASSED_TESTS=$((PASSED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 3.14: Filter Transactions by Category
# Alice has Rent (Category ID: 3)
run_request "GET" "/transactions?categoryId=3" "$COOKIE_ALICE" ""
assert_test "Get transactions filtered by Category ID (Success 200)" "200" "$LAST_STATUS" "transactions" "$LAST_BODY"
assert_test "Verify Rent txn present" "200" "$LAST_STATUS" "Apartment rent payment" "$LAST_BODY"
if echo "$LAST_BODY" | grep -q "Monthly Main Job Salary"; then
    echo -e "\033[0;31m[FAIL]\033[0;0m Category Filter failed: included wrong categories"
    FAILED_TESTS=$((FAILED_TESTS + 1))
else
    echo -e "\033[0;32m[PASS]\033[0;0m Category Filter correct: only returns filtered category"
    PASSED_TESTS=$((PASSED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 3.15: Update Transaction (Update amount and description)
run_request "PUT" "/transactions/$TXN_ALICE_INC_ID" "$COOKIE_ALICE" '{"amount":5500.00,"categoryName":"Salary","description":"Updated Salary Amount"}'
assert_test "Update transaction amount and description (Success 200)" "200" "$LAST_STATUS" "Updated Salary Amount" "$LAST_BODY"
assert_test "Verify updated amount" "200" "$LAST_STATUS" "5500" "$LAST_BODY"

# Test 3.16: Verification of Date Immutability on Update
# The original date was 2026-05-01. We try to put "2026-05-02" in payload - it should NOT update date
run_request "PUT" "/transactions/$TXN_ALICE_INC_ID" "$COOKIE_ALICE" '{"amount":5500.00,"date":"2026-05-02","categoryName":"Salary"}'
assert_test "Request update with new date (Success 200)" "200" "$LAST_STATUS" "" "$LAST_BODY"
if echo "$LAST_BODY" | grep -q "2026-05-02"; then
    echo -e "\033[0;31m[FAIL]\033[0;0m Crucial Rule Broken: Transaction date is mutable!"
    FAILED_TESTS=$((FAILED_TESTS + 1))
else
    echo -e "\033[0;32m[PASS]\033[0;0m Crucial Rule Upheld: Transaction date is immutable"
    PASSED_TESTS=$((PASSED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 3.17: Delete Transaction (Delete custom category txn to allow category deletion)
run_request "DELETE" "/transactions/$TXN_ALICE_CUST_ID" "$COOKIE_ALICE" ""
assert_test "Delete Alice's custom transaction (Success 200)" "200" "$LAST_STATUS" "deleted successfully" "$LAST_BODY"

# Test 3.18: Verify get transaction after delete
run_request "GET" "/transactions" "$COOKIE_ALICE" ""
if echo "$LAST_BODY" | grep -q "Web Design Gig"; then
    echo -e "\033[0;31m[FAIL]\033[0;0m Transaction remains in list after deletion"
    FAILED_TESTS=$((FAILED_TESTS + 1))
else
    echo -e "\033[0;32m[PASS]\033[0;0m Transaction successfully deleted from transaction list"
    PASSED_TESTS=$((PASSED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# Test 3.19: Now delete Custom Category (Freelance) since it's no longer in use
run_request "DELETE" "/categories/Freelance" "$COOKIE_ALICE" ""
assert_test "Delete Freelance custom category (Success 200)" "200" "$LAST_STATUS" "deleted successfully" "$LAST_BODY"


echo -e "\n----------------------------------------------------------"
echo " 4. SAVINGS GOALS & TRACKING TESTS"
echo "----------------------------------------------------------"

# Test 4.1: Create Goal with Target Date in Past
run_request "POST" "/goals" "$COOKIE_ALICE" '{"goalName":"Old Goal","targetAmount":1000.00,"targetDate":"2020-01-01"}'
assert_test "Create goal with past target date (Bad Request 400)" "400" "$LAST_STATUS" "must be in the future" "$LAST_BODY"

# Test 4.2: Create Goal Validation - Missing Required Target Amount
run_request "POST" "/goals" "$COOKIE_ALICE" '{"goalName":"Car Fund","targetDate":"2028-12-31"}'
assert_test "Create goal with missing target amount (Bad Request 400)" "400" "$LAST_STATUS" "targetAmount:" "$LAST_BODY"

# Test 4.3: Create Goal - Success with default startDate (today)
run_request "POST" "/goals" "$COOKIE_ALICE" '{"goalName":"New Laptop","targetAmount":2000.00,"targetDate":"2027-12-31"}'
assert_test "Create Laptop Goal for Alice (Success 201)" "201" "$LAST_STATUS" "New Laptop" "$LAST_BODY"
GOAL_ALICE_ID=$(extract_json_val "$LAST_BODY" "id")

# Test 4.4: Create Goal - Success with custom startDate
run_request "POST" "/goals" "$COOKIE_ALICE" '{"goalName":"Dream Trip","targetAmount":10000.00,"targetDate":"2028-06-01","startDate":"2026-05-01"}'
assert_test "Create Trip Goal with start date (Success 201)" "201" "$LAST_STATUS" "Dream Trip" "$LAST_BODY"
assert_test "Verify Custom Start Date in output" "201" "$LAST_STATUS" "2026-05-01" "$LAST_BODY"
GOAL_ALICE_TRIP_ID=$(extract_json_val "$LAST_BODY" "id")

# Let's check the progress details of the Dream Trip Goal
# Current Alice financial standing since 2026-05-01 is:
# INCOME: 5500.00 (Salary)
# EXPENSE: 1200.00 (Rent)
# NET PROGRESS = 5500 - 1200 = 4300.00
# Target Amount = 10000.00
# Remaining Amount = 10000 - 4300 = 5700.00
# Progress Percentage = (4300 / 10000) * 100 = 43%
assert_test "Verify Dream Trip Current Progress = 4300.00" "201" "$LAST_STATUS" '"currentProgress":4300' "$LAST_BODY"
assert_test "Verify Dream Trip Remaining Amount = 5700.00" "201" "$LAST_STATUS" '"remainingAmount":5700' "$LAST_BODY"
assert_test "Verify Dream Trip Progress Percentage = 43%" "201" "$LAST_STATUS" '"progressPercentage":43' "$LAST_BODY"

# Test 4.5: Create Goal for Bob
run_request "POST" "/goals" "$COOKIE_BOB" '{"goalName":"Bob House Fund","targetAmount":100000.00,"targetDate":"2030-01-01"}'
assert_test "Create Bob Goal (Success 201)" "201" "$LAST_STATUS" "Bob House Fund" "$LAST_BODY"
GOAL_BOB_ID=$(extract_json_val "$LAST_BODY" "id")

# Test 4.6: Data Isolation - Alice tries to GET Bob's goal by ID
run_request "GET" "/goals/$GOAL_BOB_ID" "$COOKIE_ALICE" ""
assert_test "Alice views Bob's goal (Not Found 404)" "404" "$LAST_STATUS" "not found" "$LAST_BODY"

# Test 4.7: Data Isolation - Alice tries to PUT update Bob's goal
run_request "PUT" "/goals/$GOAL_BOB_ID" "$COOKIE_ALICE" '{"goalName":"Stolen Bob Goal","targetAmount":1.00}'
assert_test "Alice updates Bob's goal (Not Found 404)" "404" "$LAST_STATUS" "not found" "$LAST_BODY"

# Test 4.8: Data Isolation - Alice tries to DELETE Bob's goal
run_request "DELETE" "/goals/$GOAL_BOB_ID" "$COOKIE_ALICE" ""
assert_test "Alice deletes Bob's goal (Not Found 404)" "404" "$LAST_STATUS" "not found" "$LAST_BODY"

# Test 4.9: Get all goals for Alice
run_request "GET" "/goals" "$COOKIE_ALICE" ""
assert_test "Get Alice's goals (Success 200)" "200" "$LAST_STATUS" "New Laptop" "$LAST_BODY"
assert_test "Verify both goals returned" "200" "$LAST_STATUS" "Dream Trip" "$LAST_BODY"

# Test 4.10: Get Alice's goal by ID
run_request "GET" "/goals/$GOAL_ALICE_ID" "$COOKIE_ALICE" ""
assert_test "Get Alice's Laptop Goal by ID (Success 200)" "200" "$LAST_STATUS" "New Laptop" "$LAST_BODY"

# Test 4.11: Update Alice's Goal
run_request "PUT" "/goals/$GOAL_ALICE_ID" "$COOKIE_ALICE" '{"goalName":"Ultra Laptop","targetAmount":2500.00}'
assert_test "Update Alice Laptop Goal (Success 200)" "200" "$LAST_STATUS" "Ultra Laptop" "$LAST_BODY"

# Test 4.12: Delete Alice's Laptop Goal
run_request "DELETE" "/goals/$GOAL_ALICE_ID" "$COOKIE_ALICE" ""
assert_test "Delete Alice's goal (Success 200)" "200" "$LAST_STATUS" "Goal deleted successfully" "$LAST_BODY"


echo -e "\n----------------------------------------------------------"
echo " 5. REPORTS GENERATION TESTS"
echo "----------------------------------------------------------"

# Add an additional transaction to verify aggregation merges
run_request "POST" "/transactions" "$COOKIE_ALICE" '{"amount":150.00,"date":"2026-05-12","categoryName":"Rent","description":"Additional rent utility bill"}'
assert_test "Add another Rent transaction (Success 201)" "201" "$LAST_STATUS" "Rent" "$LAST_BODY"

# Test 5.1: Monthly Report - May 2026
# Expected values:
# Income Category 'Salary': 5500.00
# Expense Category 'Rent': 1200 + 150 = 1350.00
# Net Savings = 5500 - 1350 = 4150.00
run_request "GET" "/reports/monthly/2026/5" "$COOKIE_ALICE" ""
assert_test "Get Monthly Report May 2026 (Success 200)" "200" "$LAST_STATUS" "netSavings" "$LAST_BODY"
assert_test "Verify monthly incomeByCategory 'Salary': 5500.00" "200" "$LAST_STATUS" '"Salary":5500' "$LAST_BODY"
assert_test "Verify monthly expenseByCategory 'Rent': 1350.00" "200" "$LAST_STATUS" '"Rent":1350' "$LAST_BODY"
assert_test "Verify monthly netSavings: 4150.00" "200" "$LAST_STATUS" '"netSavings":4150' "$LAST_BODY"

# Test 5.2: Monthly Report - Empty Month (January 2026)
run_request "GET" "/reports/monthly/2026/1" "$COOKIE_ALICE" ""
assert_test "Get Monthly Report January 2026 (Success 200)" "200" "$LAST_STATUS" "netSavings" "$LAST_BODY"
assert_test "Verify zero income in empty month" "200" "$LAST_STATUS" '"totalIncome":0' "$LAST_BODY"

# Test 5.3: Yearly Report - 2026
run_request "GET" "/reports/yearly/2026" "$COOKIE_ALICE" ""
assert_test "Get Yearly Report 2026 (Success 200)" "200" "$LAST_STATUS" "monthlyBreakdown" "$LAST_BODY"
assert_test "Verify yearly totalIncome: 5500.00" "200" "$LAST_STATUS" '"totalIncome":5500' "$LAST_BODY"
assert_test "Verify yearly totalExpenses: 1350.00" "200" "$LAST_STATUS" '"totalExpenses":1350' "$LAST_BODY"
assert_test "Verify yearly netSavings: 4150.00" "200" "$LAST_STATUS" '"netSavings":4150' "$LAST_BODY"


echo -e "\n----------------------------------------------------------"
echo " 6. LOGOUT & COOKIE DESTRUCTION TESTS"
echo "----------------------------------------------------------"

# Test 6.1: Logout Alice
run_request "POST" "/auth/logout" "$COOKIE_ALICE" ""
assert_test "Logout Alice (Success 200)" "200" "$LAST_STATUS" "Logout successful" "$LAST_BODY"

# Test 6.2: Try to get Alice's categories now
run_request "GET" "/categories" "$COOKIE_ALICE" ""
assert_test "GET categories after logout (Unauthorized 401)" "401" "$LAST_STATUS" "Unauthorized" "$LAST_BODY"


echo -e "\n=========================================================="
echo "   TEST SUMMARY"
echo "=========================================================="
echo "Total Tests Run:  $TOTAL_TESTS"
echo "Passed:          $PASSED_TESTS"
echo "Failed:          $FAILED_TESTS"

if [ "$TOTAL_TESTS" -gt 0 ]; then
    SUCCESS_RATE=$(( PASSED_TESTS * 100 / TOTAL_TESTS ))
    echo "Success Rate:    $SUCCESS_RATE%"
else
    echo "Success Rate:    0%"
fi
echo "=========================================================="

if [ "$FAILED_TESTS" -eq 0 ]; then
    echo -e "\n\033[0;32m🎉 ALL INTEGRATION TESTS PASSED SUCCESSFULLY! Ready for production deployment.\033[0;0m"
    exit 0
else
    echo -e "\n\033[0;31m⚠️  SOME TESTS FAILED. Please review the errors.\033[0;0m"
    exit 1
fi
