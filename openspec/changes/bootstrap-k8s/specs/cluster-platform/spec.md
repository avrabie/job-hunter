# Spec Delta

## Purpose

Describes how the existing single-node cluster is prepared so that the application can be deployed on it, and how that preparation is checked.

## ADDED Requirements

### Requirement: Reproducible cluster setup guide
The repository SHALL contain step-by-step instructions that take a plain single-node Kubernetes cluster with a working kubeconfig to a cluster ready for the application, listing each component with its pinned version and a command to verify it.

#### Scenario: Complete guide
- **WHEN** an operator follows `k8s/README.md` on a plain single-node cluster from the first step
- **THEN** the cluster ends with a default storage class, Gateway API, a Gateway implementation reachable on the node's ports 80 and 443, and cert-manager, each verified by the command given in the guide

#### Scenario: Starting checks
- **WHEN** the operator runs the guide's first checks
- **THEN** they confirm the node is Ready and can run ordinary workloads, and that the CNI enforces network policies

#### Scenario: Pinned versions
- **WHEN** the guide installs a component
- **THEN** the exact version is stated, with no `latest` tag

### Requirement: Gateway API available
The cluster SHALL have the Gateway API CRDs installed and a Gateway implementation that accepts `Gateway` and `HTTPRoute` resources.

#### Scenario: Gateway class present
- **WHEN** the operator lists gateway classes after setup
- **THEN** one class is accepted and ready for use

#### Scenario: Route accepted
- **WHEN** an `HTTPRoute` referencing the gateway is applied
- **THEN** its status reports it as accepted

### Requirement: Automated certificates
The cluster SHALL run cert-manager with a staging and a production Let's Encrypt issuer, so the edge can obtain certificates without manual steps.

#### Scenario: Staging certificate
- **WHEN** a certificate is requested from the staging issuer for a hostname that points to the cluster
- **THEN** the certificate becomes ready

#### Scenario: Issuer separation
- **WHEN** the production overlay selects an issuer
- **THEN** it names the production issuer, and the local overlay names none

### Requirement: Persistent storage
The cluster SHALL provide a default storage class that supports a single-node persistent volume claim, installing one if absent, and the guide SHALL state that local node disk is a single point of failure.

#### Scenario: Claim bound
- **WHEN** a persistent volume claim is created without naming a storage class
- **THEN** it is bound

### Requirement: Local test cluster
The repository SHALL document one command that creates a local disposable conformant Kubernetes cluster, on which the base manifests can be applied and the routes tested with the same Gateway implementation as production.

#### Scenario: Local cluster
- **WHEN** a developer runs the documented local cluster command and applies the local overlay
- **THEN** the routes of the `edge-routing` capability work on `localhost`

### Requirement: Minimal public exposure and tunnelled access
The server's firewall SHALL allow inbound SSH, HTTP (80) and HTTPS (443) only, and the Kubernetes API server SHALL NOT be reachable from the internet; operators SHALL reach it through an SSH tunnel documented in the guide.

#### Scenario: Public ports
- **WHEN** the server is scanned from the internet after setup
- **THEN** only the SSH, HTTP and HTTPS ports respond

#### Scenario: Tunnelled kubectl
- **WHEN** the operator opens the documented SSH tunnel and uses the documented kubeconfig
- **THEN** `kubectl get nodes` shows the node Ready

#### Scenario: Pod networking intact
- **WHEN** the firewall rules are in place
- **THEN** pods can reach each other, the cluster DNS and the internet, and the edge still serves ports 80 and 443

### Requirement: Manifest validation
The repository SHALL provide one command that renders every overlay and validates the result against the Kubernetes and Gateway API schemas, and that fails on any invalid resource.

#### Scenario: Valid manifests
- **WHEN** the validation command runs on the committed manifests
- **THEN** it exits successfully for both overlays

#### Scenario: Invalid manifest
- **WHEN** a manifest has an unknown field or a wrong type
- **THEN** the command exits with an error naming the resource
