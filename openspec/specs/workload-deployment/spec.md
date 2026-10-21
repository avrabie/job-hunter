# workload-deployment Specification

## Purpose
Defines how the database and the application services run in the cluster: configuration and secrets, health, resource limits, rollout behaviour on one node, network isolation, and the local and production variants.

## Requirements

### Requirement: Workload set
The base manifests SHALL deploy exactly these workloads in one namespace: `postgres`, `backend`, `auth`, `gateway` and `ui`, where `ui` runs the web UI image and carries no page content in a ConfigMap.

#### Scenario: Workloads present
- **WHEN** the base is rendered
- **THEN** it contains a workload and a cluster-internal service for each of the five names, and no others

#### Scenario: UI from an image
- **WHEN** the `ui` workload is rendered
- **THEN** it uses an application image with an explicit tag and mounts no ConfigMap holding page content

### Requirement: Databases created on first start
The `postgres` workload SHALL run a pgvector-enabled PostgreSQL on a persistent volume and SHALL create the databases `authdb` and `jobhunter`, each with its own role, and the `vector` extension in `jobhunter`, on first start only.

#### Scenario: First start
- **WHEN** `postgres` starts with an empty volume
- **THEN** both databases and roles exist, and `vector` is installed in `jobhunter`

#### Scenario: Restart keeps data
- **WHEN** the `postgres` pod is deleted and recreated
- **THEN** it starts with the same data and the initialisation does not run again

#### Scenario: Role separation
- **WHEN** the `auth` role connects to `jobhunter`
- **THEN** the connection is refused, and the same holds for the `backend` role on `authdb`

### Requirement: Secrets kept out of the repository
Passwords and keys SHALL be supplied through Kubernetes Secrets created from an uncommitted file. The repository SHALL contain only an `.example` template, and the committed manifests SHALL contain no real credential.

#### Scenario: Template only
- **WHEN** the repository is searched for credentials
- **THEN** only placeholder values in `*.example` files are found

#### Scenario: Missing secret
- **WHEN** a workload is applied without its secret
- **THEN** its pod does not start, and the event names the missing secret

### Requirement: Configuration from environment
Each application workload SHALL receive its database connection, public issuer URI and target service addresses from the environment, using the setting names its application reads, and never from values baked into an image.

#### Scenario: Backend settings
- **WHEN** the `backend` pod starts
- **THEN** it connects to `jobhunter` with its own role and validates tokens issued by the public issuer URI

#### Scenario: Gateway targets
- **WHEN** the `gateway` pod starts
- **THEN** its routes point to the in-cluster services `backend` and `auth`

### Requirement: Health probes
Every application workload SHALL declare liveness and readiness probes against its unauthenticated health endpoints, and traffic SHALL only reach a pod after its readiness probe passes.

#### Scenario: Not ready yet
- **WHEN** a pod is still starting
- **THEN** its service has no ready endpoint

#### Scenario: Database down
- **WHEN** `postgres` is down
- **THEN** `backend` and `auth` become not ready without being restarted by liveness

### Requirement: Resource bounds
Every container SHALL declare CPU and memory requests and a memory limit, so no workload can exhaust the node.

#### Scenario: All containers bounded
- **WHEN** the rendered manifests are checked
- **THEN** every container has requests and a memory limit

### Requirement: Single-node rollout
Single-replica workloads SHALL use a rollout strategy that stops the old pod before starting the new one, so a rollout cannot stay pending for lack of node capacity.

#### Scenario: Rollout on a full node
- **WHEN** a new image is rolled out while the node has room for only one of the pods
- **THEN** the old pod stops, the new pod starts, and the rollout completes

### Requirement: Database network isolation
`postgres` SHALL NOT be exposed outside the cluster, and only `backend` and `auth` SHALL be allowed to open connections to it.

#### Scenario: Allowed client
- **WHEN** `backend` connects to `postgres` on its port
- **THEN** the connection succeeds

#### Scenario: Other pod
- **WHEN** the `gateway` or `ui` pod tries to connect to `postgres`
- **THEN** the connection is blocked

#### Scenario: No external service
- **WHEN** the services of the namespace are listed
- **THEN** none of type NodePort or LoadBalancer targets `postgres`

### Requirement: No personal data in logs
Application containers SHALL log to standard output only, and the manifests SHALL NOT enable request or payload logging at the edge or the gateway.

#### Scenario: Edge logging
- **WHEN** a request with an `Authorization` header passes the edge
- **THEN** the header value is not written to any log

### Requirement: Local and production overlays
The repository SHALL provide a `local` overlay that runs on the local test cluster without a real domain or certificate, and a `prod` overlay that sets the public hostname, the production issuer and image tags, both built from the same base.

#### Scenario: Same base
- **WHEN** both overlays are rendered
- **THEN** they differ only in hostname, TLS and issuer settings, image references and resource sizes, not in the set of workloads

#### Scenario: Local routing
- **WHEN** the local overlay is applied to the local cluster
- **THEN** `http://localhost:<port>/` reaches `ui`, `/auth/.well-known/openid-configuration` reaches `auth`, and `/api/` reaches `backend` (returning 401 without a token)

### Requirement: Image references
Application images SHALL be referenced by an explicit tag set per overlay, never `latest`, and the guide SHALL document how to build each image and push it to Docker Hub by hand.

#### Scenario: Tag check
- **WHEN** the rendered `prod` overlay is checked
- **THEN** every application image has an explicit, non-`latest` tag
