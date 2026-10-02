# Задание 3.1 — Трейсинг: OpenTelemetry + Jaeger в Kubernetes (MVP)

MVP из двух сервисов, которые связаны в **один трейс** через OpenTelemetry (W3C Trace Context)
и отправляют spans в Jaeger, развёрнутый в Kubernetes (minikube) через jaeger-operator.

## Состав

| Компонент | Что это |
|---|---|
| **service-a** | «Сервис заказов» (аналог Shop/CRM API). Java Spring Boot, REST `GET /order`. Создаёт SERVER span и вызывает service-b. |
| **service-b** | «Сервис расчёта цены» (аналог MES). Java Spring Boot, REST `GET /price`. Создаёт SERVER span, привязанный к входящему `traceparent`. |
| **Jaeger** | all-in-one (in-memory) в namespace `observability`, развёрнут jaeger-operator'ом. |

## Запуск (по шагам)

```bash
# 1. Minikube (ingress-аддон по требованию исходного README)
minikube start --addons=ingress

# 2. cert-manager (нужен jaeger-operator для webhook-сертификатов)
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.3/cert-manager.yaml
kubectl rollout status deployment/cert-manager -n cert-manager --timeout=300s

# 3. Jaeger-operator + инстанс Jaeger
kubectl create namespace observability
kubectl apply -f https://github.com/jaegertracing/jaeger-operator/releases/download/v1.51.0/jaeger-operator.yaml -n observability
kubectl rollout status deployment/jaeger-operator -n observability --timeout=300s
kubectl apply -f k8s/jaeger-instance.yaml

# 4. Сборка сервисов (JDK 21 + Maven) и загрузка образов в minikube
cd services && mvn -q package -DskipTests && cd ..
docker build -t service-a:latest -f services/Dockerfile --build-arg SERVICE=service-a services/
docker build -t service-b:latest -f services/Dockerfile --build-arg SERVICE=service-b services/
docker save service-a:latest service-b:latest | minikube image load -

# 5. Деплой сервисов
kubectl apply -f k8s/services.yaml
kubectl wait --for=condition=ready pod -l app=service-a --timeout=120s
kubectl wait --for=condition=ready pod -l app=service-b --timeout=120s
```

### Вызов: service-a → service-b (один трейс)

```bash
kubectl exec -it $(kubectl get pods -l app=service-a -o jsonpath='{.items[0].metadata.name}') -- \
  wget -qO- "http://service-a:8080/order?order-id=ORD-1000&customer=customer-42"
```

Ожидаемый ответ:
```json
{"order-id":"ORD-1000","customer":"customer-42","status":"created",
 "price-calc":"{\"price\":...,\"currency\":\"RUB\",\"polygons\":...}"}
```

### Jaeger UI

```bash
kubectl port-forward svc/simplest-query 16686:16686 -n observability
```

Открыть в браузере: **http://localhost:16686**
Поиск: сервис `service-a` (или `service-b`) — в каждом trace 3 spans:
`service-a GET /order` → `service-a GET http://service-b:8080/price` (client) → `service-b GET /price`.

Сделать скриншот трейса и сохранить в `screenshots/jaeger-trace.png`.

## Как устроен трейс (кратко)

- service-a создаёт корневой SERVER span `GET /order` с атрибутом `order.id`.
- Исходящий запрос к service-b обёртывается CLIENT span; `W3CTraceContextPropagator`
  вписывает `traceparent`/`tracestate` в заголовки.
- service-b декодирует `traceparent` (TextMapGetter по заголовкам HTTP) и создаёт SERVER span
  `GET /price` **в том же трейсе**, с бизнес-атрибутами `price.amount`, `model.polygons`,
  `calculation.duration.ms`.
- Оба сервиса экспортируют spans по OTLP/HTTP (4318) в `simplest-collector.observability.svc` (Jaeger all-in-one).

## Структура

```
k8s/
├── jaeger-instance.yaml   # Jaeger (allInOne, in-memory)
└── services.yaml          # Deployment + Service для service-a/service-b (в точности как в оригинальном репозитории)
services/
├── pom.xml                # parent: Spring Boot + OpenTelemetry BOM
├── Dockerfile             # runtime-образ (non-root)
├── service-a/             # «сервис заказов»
└── service-b/             # «сервис расчёта цены»
screenshots/               # сюда — скриншот трейса
```
