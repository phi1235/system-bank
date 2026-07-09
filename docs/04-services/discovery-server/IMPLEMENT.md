# IMPLEMENT — discovery-server

## Goal

Eureka Server only.

## Path

`backend/discovery-server/`

## Stack

- spring-cloud-starter-netflix-eureka-server
- Port 8761
- `eureka.client.register-with-eureka=false`
- `eureka.client.fetch-registry=false`

## Checklist

- [ ] Bootstrap app + `@EnableEurekaServer`
- [ ] application.yml
- [ ] Dockerfile
- [ ] Compose service + health
- [ ] README snippet: open http://localhost:8761

## Out of scope

- Auth on Eureka UI (dev open)
- HA peer replication

## Done when

Services can register and appear on Eureka dashboard.
