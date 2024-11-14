.PHONY: azure idporten maskinporten

azure:
	ENV_FILE="./wonderwalled-azure/local.env" docker-compose up -d && ./gradlew wonderwalled-azure:run

idporten:
	ENV_FILE="./wonderwalled-idporten/local.env" docker-compose up -d && ./gradlew wonderwalled-idporten:run

maskinporten:
	ENV_FILE="./wonderwalled-maskinporten/local.env" docker-compose --profile otel up -d && ./gradlew wonderwalled-maskinporten:run
