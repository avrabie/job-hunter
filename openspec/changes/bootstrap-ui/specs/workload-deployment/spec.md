# Spec Delta

## MODIFIED Requirements

### Requirement: Workload set
The base manifests SHALL deploy exactly these workloads in one namespace: `postgres`, `backend`, `auth`, `gateway` and `ui`, where `ui` runs the web UI image and carries no page content in a ConfigMap.

#### Scenario: Workloads present
- **WHEN** the base is rendered
- **THEN** it contains a workload and a cluster-internal service for each of the five names, and no others

#### Scenario: UI from an image
- **WHEN** the `ui` workload is rendered
- **THEN** it uses an application image with an explicit tag and mounts no ConfigMap holding page content
