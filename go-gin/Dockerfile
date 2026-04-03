FROM golang:1.22-alpine AS build
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /fhir-api ./cmd/server

FROM alpine:3.19
WORKDIR /app
COPY --from=build /fhir-api .
EXPOSE 8082
ENTRYPOINT ["./fhir-api"]
