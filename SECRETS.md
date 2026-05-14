# Secrets necessários no GitHub Actions

## Repositório: grupolink-backend

### AWS / ECS
| Secret | Descrição |
|---|---|
| `AWS_ACCESS_KEY_ID` | IAM Access Key com permissões ECS + ECR + SSM |
| `AWS_SECRET_ACCESS_KEY` | IAM Secret Key |
| `AWS_REGION` | Ex: `sa-east-1` |
| `ECR_REPOSITORY` | Nome do repositório ECR: `grupolink-backend` |
| `ECS_CLUSTER` | Nome do cluster ECS: `grupolink-prod` |
| `ECS_SERVICE` | Nome do serviço ECS: `grupolink-prod` |
| `ECS_TASK_DEFINITION` | Nome da task definition: `grupolink-backend` |

### Secrets no SSM Parameter Store
Todos os parâmetros devem ser criados em `/grupolink/NOME`:
```bash
aws ssm put-parameter --name "/grupolink/DB_HOST" --value "..." --type SecureString
aws ssm put-parameter --name "/grupolink/DB_PASSWORD" --value "..." --type SecureString
aws ssm put-parameter --name "/grupolink/JWT_SECRET" --value "..." --type SecureString
aws ssm put-parameter --name "/grupolink/MP_ACCESS_TOKEN" --value "..." --type SecureString
# ... repetir para todos os secrets do .env.example
```

### IAM Policy mínima para o CI/CD
```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["ecr:GetAuthorizationToken"], "Resource": "*" },
    { "Effect": "Allow", "Action": ["ecr:BatchCheckLayerAvailability","ecr:PutImage","ecr:InitiateLayerUpload","ecr:UploadLayerPart","ecr:CompleteLayerUpload"], "Resource": "arn:aws:ecr:*:*:repository/grupolink-backend" },
    { "Effect": "Allow", "Action": ["ecs:DescribeTaskDefinition","ecs:RegisterTaskDefinition","ecs:UpdateService","ecs:DescribeServices"], "Resource": "*" },
    { "Effect": "Allow", "Action": ["iam:PassRole"], "Resource": "arn:aws:iam::*:role/grupolink-*" }
  ]
}
```
