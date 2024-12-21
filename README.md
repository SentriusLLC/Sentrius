Sentrius

Sentrius is a secure shell access management solution that integrates zero trust principles to protect your infrastructure. Sentrius is divided into two primary Maven sub-projects: core and api. The core sub-project handles core functionalities such as enforcing zero trust policies, while the api sub-project is responsible for providing a REST API to interact with these functionalities.

Internally Sentrius is formerly known as SSO ( SecureShellOps ). 

Project Structure

Sentrius consists of the following sub-projects:

Core: The core functionality of Sentrius, which manages SSH connections and enforces security rules. This includes:

Enclave management

Zero trust policy enforcement

Secure connection handling

API: A RESTful interface for interacting with the Sentrius core functionalities. The API allows users to create, manage, and visualize SSH enclaves and security rules in a flexible way.

Features

Zero Trust Security: Implements zero trust principles to ensure every connection is authenticated and authorized in real-time.

SSH Enclaves: Manage host groupings through enclaves, providing role-based access control to specific nodes.

Dynamic Rules Enforcement: Define and enforce zero trust rules at runtime, ensuring the security policies adapt to changing contexts.

REST API: Offers a fully accessible REST API to manage SSH configurations, enclaves, and rules programmatically.

Prerequisites

Java 11 or later

Apache Maven 3.6+

A database (e.g., PostgreSQL, MySQL) configured for storing session and configuration data

Installation

Clone the Repository

$ git clone https://github.com/your-repo/sentrius.git
$ cd sentrius

Build the Project

Sentrius uses Maven for building the project. Make sure you have Maven installed.

$ mvn clean install

This command will build both the core and api sub-projects.

Configuration

Sentrius requires configuration files for both core and api to be set up before running. Create a configuration file for each module in src/main/resources/application.properties or provide your own external configuration.

Configuration properties include:

Database configuration

SSH Settings (e.g., ports, timeouts)

Security parameters for JWT or OAuth integration

Running Sentrius

Running Core

Navigate to the core sub-project and run it using Maven:

$ cd core
$ mvn spring-boot:run

Running API

Navigate to the api sub-project and run it using Maven:

$ cd api
$ mvn spring-boot:run

After running the core and api, the API server should be accessible via http://localhost:8080/api/v1/. The endpoints are defined in the api module to interact with the core module.

Usage

Create an Enclave

To create a new enclave, you can use the following API endpoint:

POST /api/v1/enclaves

Payload example:

{
"name": "Production Servers",
"description": "Access group for production nodes"
}

Adding Hosts to an Enclave

To add hosts to an enclave, use:

POST /api/v1/enclaves/{enclaveId}/hosts

Payload example:

{
"host": "192.168.1.10",
"username": "admin",
"port": 22
}

Establishing Secure Connections

Sentrius allows establishing secure SSH sessions with enforced policies using:

POST /api/v1/ssh/connect

Payload example:

{
"enclaveId": "12345",
"hostId": "67890"
}

API Documentation

API documentation is provided via Swagger. Once the api module is running, you can access the Swagger UI at:

http://localhost:8080/swagger-ui.html

Contributing

Feel free to submit issues, fork the repository, and make pull requests. Contributions are welcome to improve features, documentation, and to add more functionality.

License

Sentrius is licensed under the MIT License. See the LICENSE file for more details.

Contact

For support or questions, please contact the project maintainers at support@sentrius.io.



Deploying to EKS

```bash
eksctl create cluster \
  --name sentrius-cluster \
  --region us-east-1 \
  --nodegroup-name sentrius-nodegroup \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed \
  --tags tenant=multi-tenant,project=sentrius


```

## Create customer namesapce

```bash

kubectl create namespace sentrius-customer-1

```

### Attach policy to an iam role

```bash

eksctl create iamserviceaccount \
  --name sentrius-service-account \
  --namespace sentrius \
  --cluster sentrius-cluster \
  --attach-policy-arn arn:aws:iam::<account-id>:policy/secretsmanager-read-policy \
  --approve
```




{
"repository": {
"repositoryArn": "arn:aws:ecr:us-east-1:060808646119:repository/sentrius-keycloak",
"registryId": "060808646119",
"repositoryName": "sentrius-keycloak",
"repositoryUri": "060808646119.dkr.ecr.us-east-1.amazonaws.com/sentrius-keycloak",
"createdAt": "2024-12-20T11:47:28.432000-05:00",
"imageTagMutability": "MUTABLE",
"imageScanningConfiguration": {
"scanOnPush": false
},
"encryptionConfiguration": {
"encryptionType": "AES256"
}
}
}
{
"repository": {
"repositoryArn": "arn:aws:ecr:us-east-1:060808646119:repository/sentrius-sentrius",
"registryId": "060808646119",
"repositoryName": "sentrius-sentrius",
"repositoryUri": "060808646119.dkr.ecr.us-east-1.amazonaws.com/sentrius-sentrius",
"createdAt": "2024-12-20T11:47:28.974000-05:00",
"imageTagMutability": "MUTABLE",
"imageScanningConfiguration": {
"scanOnPush": false
},
"encryptionConfiguration": {
"encryptionType": "AES256"
}
}
}
{
"repository": {
"repositoryArn": "arn:aws:ecr:us-east-1:060808646119:repository/sentrius-ssh",
"registryId": "060808646119",
"repositoryName": "sentrius-ssh",
"repositoryUri": "060808646119.dkr.ecr.us-east-1.amazonaws.com/sentrius-ssh",
"createdAt": "2024-12-20T11:47:29.517000-05:00",
"imageTagMutability": "MUTABLE",
"imageScanningConfiguration": {
"scanOnPush": false
},
"encryptionConfiguration": {
"encryptionType": "AES256"
}
}
}

060808646119.dkr.ecr.us-east-1.amazonaws.com/sentrius-ssh

docker tag sentrius-keycloak:latest 060808646119.dkr.ecr.us-east-1.amazonaws.com/sentrius-keycloak:latest
docker tag sentrius-sentrius:latest 060808646119.dkr.ecr.us-east-1.amazonaws.com/sentrius-sentrius:latest
docker tag sentrius-ssh:latest 060808646119.dkr.ecr.us-east-1.amazonaws.com/sentrius-ssh:latest


kubectl config use-context minikube
