# LO1

## 1.1 Requirements
#### Functional Requirements
- The system must validate manifest before processing 
- The system must reject invalid manifest with an HTTP 400 response
- The system must prevent registration of an existing manifest number
- Duplicate manifests should return HTTP 409
- A successful registered manifest must get a shipment reference
- The system must allow carriers to submit shipment manifest through the POST /dispatch/manifests
- The system must get the tariff information from the external tariff service
- The system must correctly calculate parcel charges according the pricing formula
- Only Rated parcel should be added to the total charge
- The system must store shipment metadata in DynamoDB
- The system must archive the original manifest in Amazon S3
- The original manifest must be preserved byte-for-byte
- The system must allow users to get the original archived manifest 
- The system must allow users to get a shipment with shipment reference
- The system must allow users to delete existing shipments
- The system must allow users to get all shipment connected to a particular carrier
- If there is no tariff, the system must mark a parcel Unrated but allow registration
- If there is no shipment, the system should return HTTP 404
- Unexpected failures should return HTTP 500
- Failures during registration should not leave inconsistent stored data 

#### Non-Functional Requirements
- Controllers, services, repositories, clients, models and DTOs should have separate responsibilities
- Access to tariff service, S3 and DynamoDB should be handled through interfaces
- The system must work as a spring boot service within a docker container
- The system should allow components to be changed or tested without changed application

#### Measurable Requirements
- Parcel charges must be correct and rounded up to two decimal places using HALF_UP
- The system should avoid having inconsistent data between the S3 and DynamoDB when registration fails 
- Carrier shipments should follow dispatch date ordering
- Archived manifests must retain the original submitted data
- If external service failure, the system must still allow shipment registration
- The system should return expected status codes for requests and errors

#### Qualitative Requirements
- The system should handle invalid inputs, missing parts and external service failures without unexpected crashes
- Error handling and logging should not expose sensitive data 
- It should be designed so that dependencies can be replaced with mocks or tests so behaviours can be tested independently
- Controllers should handle HTTP requests and responses
- Meaningful names should be used for classes, methods and variables to make code easier to read

## 1.2 Level of Requirements
#### Unit test
- ChargeCalculator should be calculated correctly using specified tariff, parcel weight and fuel surcharge
- Money should be calculated using half-up (round to two decimal places)
- Should distinguish between valid and invalid manifests
- ManifestReader should process valid JSON correctly and handle invalid ones
- The mapper should correctly convert between the different representations without losing information
- The ShipmentRegistrationService using mocked dependencies should verify successful registration, duplicate manifest detection and the handling of unrated parcels
- The total charge should only include rated parcels
- Individual parts should have correct responses to exceptions and invalid inputs

#### Integration test
- The tariff client with a mock HTTP server to verify that HTTP responses from the external tariff service are correctly received, interpreted and converted into application data
- The registration service and shipment repository should be in sync when saving and getting shipment information
- The system should correctly archive the original manifest and get the correct content when requested
- Shipment metadata should be correctly stored, listed by carrier, retrieve and deleted
- The system should handle failure scenarios involving storage components to verify that an unsuccessful registration does not leave incomplete or inconsistent data behind
- Data serialisation and deserialization to ensure that JSON and Java objects are converted correctly when information is transferred between application components and external services

#### System test 
- A carrier should submit a valid manifest through the HTTP and get a successful response with the registered shipment
- Submit a valid manifest through the POST /dispatch/manifests endpoint and verify that the system returns a successful response containing a shipment reference
- The original manifest should be accessible
- The system should return the correct shipments for a carrier in ascending dispatch date order
- A shipment should be deletable and cannot be gotten after
- Correct status codes should be returned
- Register valid shipment and if tariff unavailable then mark Unrated
- The application should run as a Dockerized Spring Boot service and expose its HTTP endpoints correctly
- Attempt to register a manifest with an existing manifest number and verify that the API returns HTTP 409
- Delete an existing shipment and verify that a subsequent retrieval attempt returns HTTP 404
- Submit malformed JSON and invalid manifests and verify that the API returns HTTP 400

## 1.3 Testing Approach
|                      What is being tested                      |              Type              |                                                                                                                              Approach                                                                                                                              |
|:--------------------------------------------------------------:|:------------------------------:|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|                Parcel charge (ChargeCalculator)                |          Unit (JUnit)          |                       Provide known tariff values and parcel weights, then compare the calculated charge against the expected result. Test different weights, base fees and fuel surcharges to verify that the formula is applied correctly.                       |
|                  Rounding (ChargeCalculator)                   |          Unit (JUnit)          |                                               Test values that require rounding to two decimal places. Include values around rounding boundaries to verify that HALF_UP is used rather than another rounding method.                                               |
|              Manifest validation (ManifestReader)              |          Unit (JUnit)          |                                        Supply valid and invalid manifests. Test missing required fields, malformed JSON and incorrectly formatted values. Verify that invalid manifests are rejected rather than processed.                                        |
|               Manifest mapping (mapper classes)                |          Unit (JUnit)          |                             Provide representative input objects and verify that the mapped output contains the correct values. Check that required fields are preserved and that optional or unknown fields are handled as expected.                              |
|      Shipment registration (ShipmentRegistrationService)       |    Unit (JUnit and Mockito)    |                   Mock the repository, archive and tariff client. Test successful registration, duplicate manifest detection and registration where one or more parcels cannot be rated. Verify the expected interactions with each dependency.                    |
|                     Total shipment charge                      |          Unit (JUnit)          |                                           Provide a shipment containing both rated and unrated parcels. Verify that the total charge includes only rated parcel charges and that the parcel count includes all parcels.                                            |
|                Tariff responses (TariffClient)                 | Integration (mock HTTP server) |                          Simulate different HTTP responses from the tariff service, including a successful response, an unknown tariff response and server errors. Verify that each response is interpreted correctly by the application.                          |
|                    S3 archiving (S3Archive)                    |          Integration           |                                 Submit a raw manifest and verify that the archived content matches the original input byte-for-byte. Include an additional or unrecognised JSON field to ensure it is not lost during processing.                                  |
|          DynamoDB shipment (DynamoShipmentRepository)          |          Integration           |                                  Save a shipment and retrieve it using its shipment reference. Verify that the stored information matches the original shipment. Also test listing shipments by carrier and deleting a shipment.                                   |
| Registration failure and cleanup (ShipmentRegistrationService) |     Integration (Mockito)      |                      Simulate a failure during the DynamoDB write after the manifest has been archived. Verify that the service handles the failure and performs the required cleanup so that an incomplete registration is not left behind.                       |
|                 Complete registration workflow                 |             System             |                       Submit a valid manifest through the HTTP API. Verify that the response indicates successful registration and that the shipment can subsequently be retrieved. Check that the original manifest has also been archived.                       |
|                Shipment retrieval and deletion                 |    System (HTTP/API tests)     |                                                        Register a shipment, retrieve it using its reference, delete it and attempt to retrieve it again. Verify the expected HTTP responses at each stage.                                                         |
|                    Carrier shipment listing                    |             System             |                                           Register multiple shipments for a carrier with different dispatch dates. Retrieve the carrier's shipments and verify that they are returned in ascending dispatch-date order.                                            |
|            Invalid requests and duplicate manifests            |    System (HTTP/API tests)     |                                   Submit malformed JSON and invalid manifests, then attempt to register a manifest with an existing manifestNo. Verify that the application returns the appropriate HTTP 400 and 409 responses.                                    |
|                  Missing shipment references                   |    System (HTTP/API tests)     |                                             Request a shipment and its archived manifest using a non-existent shipment reference. Verify that the application returns HTTP 404 rather than an unexpected server error.                                             |
|                External tariff service failure                 |             System             |                                                  Simulate an unavailable tariff service and register an otherwise valid manifest. Verify that registration completes and affected parcels are marked as UNRATED.                                                   |
|             Application robustness and deployment              | System (Docker and HTTP tests) |       Run the application in its Docker container and send requests to the available endpoints. Test both successful and unsuccessful requests, checking that the application responds with the expected HTTP status codes and does not unexpectedly crash.        |
|                  S3 and DynamoDB integration                   |  Integration (Test containers  |            Run containerised instances of the storage dependencies and test actual saving, retrieval and deletion operations. Verify that the application correctly interacts with these services rather than relying exclusively on mocked responses.             |
|                         Code coverage                          |             JaCoCo             | Use JaCoCo to measure statement/line and branch coverage across the test suite. Identify methods, conditional branches and classes that are not adequately exercised by existing tests. Use the coverage report to identify areas requiring additional test cases. |


## 1.4 Asses Testing Approach
#### JUnit for unit testing
It is appropriate for testing the individual components of the Dispatch Manifest Hub, particularly the 
calculation, validation and mapping logic ads they have predictable inputs and outputs, allowing expected results to be 
compared against actual results. In addition, defects can be isolated to individual methods or classes, making them 
easier to identify and resolve. However, passing unit tests does not guarantee that components will work correctly when combined.

#### Mockito for external dependencies
Mockito provides reproducible testing conditions by allowing external dependencies to return predefined responses. 
The Dispatch Manifest Hub relies on the tariff service, S3 and DynamoDB, all of which introduce dependencies that can 
make testing less predictable. By mocking these dependencies, specific scenarios can be reproduced consistently. 
However, it does not fully reproduce the behaviour of the real dependency thus tests using mocks could continue passing 
despite an integration problem.

#### Integration tests
Testing individual classes independently cannot demonstrate that data is transferred and stored correctly between these 
components thus integration tests are needed. However, they can be more complicated and slower to execute than unit tests, 
and may also require additional configuration and test infrastructure. But they are still needed as failures between 
components could affect the correctness of the entire registration process

#### System tests
System tests verify that the complete Spring Boot application behaves correctly through its HTTP endpoints. Testing in 
the Docker environment also checks that the application can be deployed and accessed in the intended environment.

#### Negative tests and fault injection and JaCoCo
The negative tests checks that the Dispatch Manifest Hub handle a range of failure scenarios as  successful execution 
alone does not demonstrate that the application is robust. A system must also behave predictably when its inputs are 
invalid or its dependencies fail. However, it is not possible to test every potential failure combination. JaCoCo 
provides measurable evidence of how much of the source code is executed during testing. It measures line and branch 
coverage, helping identify methods, statements and conditional branches that are not exercised by the existing test suite. 
This is useful for identifying areas where additional test cases may be required.

#### Improvements
One improvement would be to introduce load and performance testing to evaluate how the system behaves when processing a 
large number of shipment registrations. Load testing could have been used to measure response times and behaviour when processing large numbers of shipment 
registrations. Ideally, tests should be developed alongside the implementation rather than being written after the 
application is complete. Writing tests during development allows defects to be identified earlier. 