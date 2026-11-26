# CW2 Student Template — CampusShop DataHub: Dispatch Manifest Hub

A **starter project** for Coursework 2. The package structure, DTOs, domain model, configuration,
controllers, the error handler, the simulated tariff client, the in-memory test doubles (so you can
run locally without Docker), the Dockerfile, docker-compose and a runnable `.http` request file are
provided; the **core logic is left for you to implement** (search for `TODO` /
`UnsupportedOperationException`).

> Read the full task in the Coursework 2 specification.

## What you implement

| File | What's missing |
|---|---|
| `service/ShipmentRegistrationService.java` | The registration flow: duplicate check (409), archive in S3, price every parcel, index in DynamoDB, clean-up if the DynamoDB write fails (spec §3.3, §4.0) |
| `service/ShipmentQueryService.java` | Look-up by reference and by carrier, the archived manifest, delete (and your deletion policy) |
| `service/ChargeCalculator.java` | `(baseFee + pricePerKg × weightKg) × (1 + fuelSurchargeRate)`, half-up to 2 decimals |
| `mapper/ManifestMapper.java` | Tariff JSON → internal model; model → `ShipmentRecord` / `ShipmentSummary` |
| `client/RestTariffClient.java` | The HTTP call to the external tariff service (spec §3) |
| `repository/S3ManifestArchive.java` | The S3 manifest archive (spec §4.1) |
| `repository/DynamoShipmentRepository.java` | The DynamoDB shipment table and its two indexes (spec §4.2, Appendix D) |
| `src/test/.../*Test.java` | The unit and API tests (skeletons with `@Disabled` are provided) |

The controllers in `controller/` are already wired to the services — the endpoints answer once the
service methods are implemented.

Provided so you can develop and run locally without AWS: `SimulationTariffClient`,
`InMemoryManifestArchive`, `InMemoryShipmentRepository`, `ManifestReader` (parses and validates the
raw body), `ProblemReportAdvice`, `config/`, `dto/`, `model/`, `exception/`, the repository and client
interfaces, `application.yml`, `Dockerfile`, `docker-compose.yml` and
`http/dispatch-manifest-hub.http`.

## Run modes

- **Local, no Docker:** run with the `inmemory` profile and simulated tariffs —
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=inmemory -Dspring-boot.run.arguments=--tariff-service.mode=simulation`.
  S3 and DynamoDB are replaced by in-memory maps.
- **Cloud (default, and what the marker uses):** any other profile — the real S3/DynamoDB repositories
  and the REST tariff client run against LocalStack and the reference tariff service
  (`docker compose up -d localstack aws-init tariff-service`). The bucket, the table and the tariff
  service are **pre-created** by the environment; your code never provisions them.

## Build, run, test

```bash
./mvnw clean package          # build + run tests

# Docker delivery format (single image — see the spec)
docker build -t ilp:cw2 .
docker save ilp:cw2 -o ilp_submission_image.tar
```

The test skeletons are annotated `@Disabled("TODO: implement")` so the project builds green from the
start. Run the API checks in `http/dispatch-manifest-hub.http` (IntelliJ HTTP Client) against the
running app.

## Submission

Submit `ilp_submission_image.tar` plus the `source/` directory (all code + the `Dockerfile`). No
README is required. See the spec §5.

## Check your submission before uploading

`check_submission.sh` verifies — the same way the auto-marker does — that your
`ilp_submission_image.tar` loads as **one tagged image**, exposes port **8080**, starts as a
container on a dynamic host port and answers `GET /actuator/health`:

```bash
./check_submission.sh ilp_submission_image.tar
```

A missing backing service locally produces a **WARNING** with instructions (the marker
provides the infrastructure itself); a hard **FAIL** means the marker would fail too.
Requires Docker and `curl`; on Windows run it in Git Bash or WSL.

## Full marker dry run (`dry_run.sh`)

`dry_run.sh` goes one step further and performs the **complete marker dry run of
spec Appendix C.1**: it rebuilds the image, exports `ilp_submission_image.tar`,
deletes the local tag and loads the image back **from the tar alone** (exactly what
the marker sees), stands up the external environment via Compose (LocalStack +
`aws-init` + the tariff service in marking mode), runs your container **on the
Compose network** with the marker-style environment variables and a dynamic host
port, waits for health `UP`, drives marked request **P3** (`POST /dispatch/manifests`),
lists the carrier's shipments (P7) and tears everything down again:

```bash
./dry_run.sh                  # full dry run, from the project root
./dry_run.sh --keep           # leave container + environment running for your own curls
./dry_run.sh --timeout 180    # slower machine: wait longer for start-up
```

How to read the output:

- **OK** — the step behaved exactly as the marker expects.
- **WARN** — the request worked, but a value differs from the spec sample; the line
  says what was missing. Understand every warning before you upload.
- **FAIL** — the marker's run would fail at this step; the script stops and prints
  the last container log lines.
- **`RESULT: PASSED`** — the `ilp_submission_image.tar` the script just wrote is
  marker-ready; that exact tar is the one that goes into your submission zip.

Notes:

- Run it from the project root (`pom.xml`, `Dockerfile`, `docker-compose.yml`); it
  needs Docker (with Compose v2), Maven (it uses `./mvnw` if present) and `curl` —
  on Windows use Git Bash or WSL.
- Host ports **4566** (LocalStack) and **8082** (tariff service) must be free;
  your own container gets a dynamic port, so 8080 may stay busy.
- The teardown runs `docker compose down` — it also stops a dev stack you had
  running in this project. Restart it afterwards with `docker compose up -d`.
- Safe to re-run any time: the container (`ilp-marker-test`) and the environment
  are removed on every exit. After `--keep`, stop them with
  `docker rm -f ilp-marker-test && docker compose down`.
- If the health step times out, look at `docker logs ilp-marker-test` — typical
  causes are LocalStack not reachable or the tariff service still starting.
