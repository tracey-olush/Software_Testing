# LO2

## 2.1 Construction of the test plan
The test for Dispatch Manifest Hub was developed based on the function and non-functional requirements stated in LO1. I 
divided by approach into unit, integration and system testing to verify different components and behaviours of the application.


The plan was to test as developing, following the principles of Test-Driven Development (TDD). As each requirement was 
implemented, corresponding test cases were identified to establish the expected behaviour. This also allowed additional 
tests to be introduced when implementation decisions revealed further potential failure scenarios. 


**Unit tests** focused on components with predictable inputs and outputs. For example, ChargeCalculatorTest verifies 
that parcel charges are calculated using the correct tariff values, parcel weights and fuel surcharge. 


As the registration service used several dependencies, I used **Mockito**. It controlled the responses of the tariff client,
archive and repository, allowing individual failure scenarios to be done without relying on external services. Thus, 
ShipmentRegistrationServiceTest successfully tests registration, duplicate manifest handling and parcels that cannot be rated.


**Integration tests** verified that components behaved correctly when connected to external dependencies. The tariff client 
tests simulate HTTP responses and Testcontainers are used to test the storage implementations against containerised S3 and 
DynamoDB dependencies. Registration involves archiving the original manifest before saving its metadata. Thus, a failure 
during the DynamoDB write could leave an archived manifest without a corresponding shipment record. I plan to simulate this 
failure and verifying that the archive is removed, preventing an incomplete registration from remaining in storage.


**System tests** verifies the application through its HTTP endpoints. This is submitting manifests, retrieving shipments, 
accessing original archived manifests, listing shipments by carrier and deleting shipments. I also include negative scenarios 
like malformed JSON, invalid manifests, duplicate submissions and requests for non-existent shipment references. These 
tests verify that the application returns the appropriate HTTP responses rather than simply checking the happy-path.

## 2.2 Evaluate Quality of Test Plan
The test plan provides broad coverage of the application's main requirements by combining unit, integration and system 
testing. It also includes negative scenarios to assess how the application responds to invalid inputs, irretrievable tariff 
and storage operation fails. 


One good thing was testing the registration failure and storage cleanup. Since the registration uses two separate storage
systems, a successful HTTP response does not guarantee it has been stored consistently. Simulating a DynamoDB failure 
after the S3 archive operation provides evidence that the application handles partial failures rather than leaving 
incomplete records behind.


The use of Mockito and Testcontainers also provides complementary forms of testing. Mockito makes it possible to 
reproduce specific dependency responses and failure conditions consistently, while Testcontainers provides stronger 
evidence that the storage implementations work against actual containerised services. This reduces the risk of relying 
entirely on mocked behaviour that might conceal integration problems.


Though the tests cover individual operations and important failure scenarios, they cannot represent every possible 
combination of external service failures. More tests on if the cleanup operation itself fails after the DynamoDB write 
has failed. This would help establish how the application behaves when the recovery mechanism is unsuccessful. The tests 
also does not show how the application behaves under a large number of simultaneous registration requests or when 
processing manifests containing many parcels. These test could have given insight on the response times under increased 
demand. Furthermore, mocks cannot guarantee that the application will behave identically when interacting with an independently 
running external service. 


The functional and integration testing provides good coverage of the main registration and retrieval 
workflows, but the lack of load testing means performance under concurrent requests remains unverified.


## 2.3 Instrumentation of Code
I used test assertions, dependency verification and JaCoCo to assess the behaviour and test coverage of the application.


Assertions tests provide direct evidence that the application produces the expected results. For example, the charge 
calculation tests compare the calculated parcel charge against an expected value, while the registration tests verify 
the resulting shipment data and interactions with the archive and repository. Integration tests also inspect the results 
of storage operations, allowing the tests to establish whether data has been persisted and retrieved correctly. Assertions 
and verification of dependency interactions help make the internal application behaviour observable during testing.


JaCoCo was used separately to measure code coverage by recording which instructions and branches were executed during the 
testing. Its report found a 96% instruction coverage and a 100% branch coverage. These measurements indicate that the 
tests execute most of the application's recorded instructions and all of its recorded conditional branches. JaCoCo was 
only used as a supporting instrument for evaluating the completeness of the tests as coverage does not determine if 
output was correct.


## 2.4 Evaluate Instrument
JaCoCo shows a string test execution coverage. However, the report shows a significant cap in the configuration layer with 
AWSClientsConfig having only 0% instruction coverage. All 60 of its instructions, spanning 16 lines, remain not executed. 
In comparison, the other seven classes in the configuration package achieved 100% instruction coverage.


Since AWSClientsConfig is responsible for configuring the applications AWS clients, this is a significant gap. However, the
Testcontainers tests verify the S3 archive and DynamoDB repository implementations against containerised storage services.


Having more  diagnostic information around the registration and storage would improve instrumentation. Structured 
logging could make it easier to trace whether the manifest archive, DynamoDB write and any subsequent cleanup operation 
succeeded or failed. Information like shipment reference and operation being performed, would make failures easier to investigate.


Overall, the combination of assertions, dependency verification, Testcontainers and JaCoCo provides a useful level of 
observability during testing. However, Additional configuration tests and more contextual diagnostic logging would 
strengthen the instrumentation and provide greater confidence in the application's behaviour.