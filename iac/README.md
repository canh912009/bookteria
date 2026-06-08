# Bookteria IaC

Kubernetes IaC cho Bookteria, chia 2 moi truong `stg` va `prod`.

Stack duoc chon:

- Kustomize cho manifests ung dung, khong can template engine rieng.
- Helmfile cho platform add-ons va stateful dependencies.
- ingress-nginx, cert-manager, external-secrets, metrics-server.
- Bitnami charts cho MySQL, MongoDB, Kafka; Neo4j chart cho graph DB.

## Cau truc

```text
iac/
  apps/
    base/                 # Deployment, Service, ConfigMap, Ingress dung chung
    overlays/stg/         # patch cho staging
    overlays/prod/        # patch cho production
  platform/
    helmfile.yaml         # add-ons + database/message broker
    values/stg.yaml
    values/prod.yaml
```

## Trien khai

1. Tao namespace va ha tang dung Helmfile:

```powershell
helmfile -f iac/platform/helmfile.yaml -e stg apply
helmfile -f iac/platform/helmfile.yaml -e prod apply
```

Sau khi cert-manager san sang, doi email trong `iac/platform/cert-manager/cluster-issuer.example.yaml` va apply:

```powershell
kubectl apply -f iac/platform/cert-manager/cluster-issuer.example.yaml
```

2. Tao Secret cho app tu file mau:

```powershell
Copy-Item iac/apps/overlays/stg/secret.example.env iac/apps/overlays/stg/secret.local.env
Copy-Item iac/apps/overlays/prod/secret.example.env iac/apps/overlays/prod/secret.local.env
```

Cap nhat gia tri that trong `secret.local.env`. File nay da duoc ignore. Sau do doi `secretGenerator.envs` trong overlay tu `secret.example.env` sang `secret.local.env`, hoac de CI/CD generate Secret rieng truoc khi apply.

3. Trien khai ung dung:

```powershell
kubectl apply -k iac/apps/overlays/stg
kubectl apply -k iac/apps/overlays/prod
```

## Images

Tat ca image dang dung placeholder:

```text
ghcr.io/your-org/bookteria/<service>:<tag>
```

CI/CD nen build tung service va update image tag bang:

```powershell
kubectl set image deployment/api-gateway api-gateway=ghcr.io/your-org/bookteria/api-gateway:1.2.3 -n bookteria-stg
```

Hoac commit patch image vao overlay tuong ung.

## Luu y ung dung

Raml` danepo hien tai chua co Dockerfile va cac file `application.yg gan nhieu endpoint `localhost`. Manifests nay override bang environment variables cua Spring Boot/Spring Cloud Gateway de service trong cluster goi nhau qua Kubernetes Service DNS.

`web-app` hien dang hard-code `API_GATEWAY` trong source React. De `REACT_APP_API_GATEWAY` co tac dung tren Kubernetes, Dockerfile/CI can build rieng theo moi truong hoac image Nginx can runtime config injection.

Ten host mac dinh:

- Staging: `api.stg.bookteria.local`, `web.stg.bookteria.local`
- Production: `api.bookteria.local`, `web.bookteria.local`

Doi host trong `iac/apps/overlays/*/ingress-patch.yaml` truoc khi gan domain that.
