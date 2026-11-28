# LO3

## 3.1 Range of techniques
The testing for Dispatch Manifest Hub used a combination of unit, integration and system testing, supported by code 
coverage analysis using JaCoCo. This followed the test plan established in Section 2, although not every proposed 
scenario was implemented.


Unit testing (92 tests) uses JUnit to verify individual classes and their behaviour. Mockito is used to isolate 
dependencies in service tests, allowing scenarios such as duplicate registration, tariff failures and storage errors 
to be tested without relying on the actual external services.


Integration testing (36 tests) examines interactions between application components and external dependencies. The unverified
10 tests were to se LocalStack through Testcontainers: DynamoShipmentRepositoryLocalStackTest (6 tests) verifies 
DynamoDB operations, and S3ArchiveLocalStackTest (4 tests) verifies S3 archiving behaviour.


System testing (5 tests) evaluates the application through its HTTP interface. SystemHTTPTest (4 tests) completes HTTP 
workflows and includes a load test, while SystemRestTariffTest (1 test) examines the application interacting with the 
REST tariff service.


There are 5 additional tests which focus on fault handling and lifecycle behaviour. RegistrationFaultTest (3 tests) 
examines registration failure scenarios, LifecycleTest (1 test) tests application lifecycle behaviour, and 
ShipmentDeleteFaultTest (1 test) examines failure handling during shipment deletion.


Black-box testing techniques like  equivalence partitioning, boundary-value analysis and error guessing were used.
Equivalence partitioning is used in testing for valid and invalid manifests, and different tariff outcomes. Boundary-value 
analysis is used in the charge calculations for two-decimal-place rounding boundaries to verify HALF_UP. Error guessing is
used in tests with malformed JSON, duplicate manifest numbers, unavailable tariff services, missing shipment 
references and storage failures.


Lastly, JaCoCo was used as a code coverage tool to support white-box testing by  measures instruction, line and branch 
coverage. This identifies code that has not been exercised in tests. Mockito supports controlled testing of internal 
service behaviours by isolating dependencies and verifying the interactions.

## 3.2 Evaluation criteria
#### Code coverage
JaCoCo instruction, line and branch coverage were used to measure how extensively the tests exercises the 
application's implementation. Instruction and line coverage helped identify code that had not been executed, while 
branch coverage is used for evaluating conditional logic. Thus, having 100% branch coverage shows that all measured 
conditional branches were addressed. Thus, functional coverage is needed alongside code coverage as code can execute 
without behaving correctly.

#### Functional coverage
Functional coverage was used to assess whether the tests adequately address the application's specified requirements. 
Evaluating the different scenarios like successful registration, duplicate manifests, and deletion helps determine 
whether the system behaves as expected from an external user's perspective. The addition of negative scenarios, like 
malformed requests and missing shipment references, also show that invalid operations produce appropriate responses.

#### Reliability
The number of successfully run tests and their pass rate were selected to evaluate whether the tests have consistent 
evidence of correct behaviour. A high pass rate indicates that the tested scenarios produced their expected outcomes.
Of the 138 tests, 128 ran and all passed. 

## 3.3 Testing results
There was a total of 138 tests. Of those 138, 128 ran successfully and passed giving a 100% pass rate. The remaining 
10 tests could not execute because Testcontainers could not be resolved in the testing environment. Thus, 92.8% of the 
planned tests were executed, while 7.2% remained unverified.


JaCoCo was used to measure code coverage across the tests and the overall results were:

|   Coverage metric    |      Result |
|:--------------------:|------------:|
| Instruction coverage |         96% |
|   Branch coverage    |        100% |
|    Line coverage     |       93.5% |
| Instructions missed  | 65 of 1,705 |
|   Branches missed    |     0 of 22 |
|     Lines missed     |   18 of 276 |

The test results and coverage report show that the implemented features were thoroughly tested.

## 3.4 Evaluate results
The results show that the tests were very effective. All the 128 tests that run passed showing that the functions tested 
behave as expected. Furthermore, the JaCoCo measured a 96% instruction coverage and a 100% branch coverage showing the tests
covered most of the application code and all branches measured.


The high instruction and line coverage suggest that relatively little of the application's implementation was left 
untested. The 100% branch coverage also shows that both outcomes of the measured conditional branches were exercised.


However, 10 tests could not be run as Testcontainers could not be resolved so S3 and DynamoDB integration using local 
stack could not be tested. This creates a gap in the container-dependent integration scenarios. Though, the remaining 
tests passed, the non-executed tests mean that not every planned test was verified. This is relevant as mocked 
repository tests cannot fully demonstrate that the application interacts correctly with the actual containerised storage services.


The uncovered AwsClientsConfig class is another limitation. Although the storage integration tests exercise the S3 and 
DynamoDB implementations, they do not necessarily exercise the application's production AWS client configuration. 
A focused Spring context test could verify that this configuration loads and creates the expected beans.


Overall, the testing results demonstrate a strong level of confidence in the reliability and correctness of the Dispatch 
Manifest Hub. All 128 tests that could be executed passed, supported by high instruction, branch and line coverage 
across the majority of the application.