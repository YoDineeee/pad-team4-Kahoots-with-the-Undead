# How zombie-service works

Read the files in this order. Each one has one job.

## 1. The path of one request

`POST /api/zombies/types` with a JSON body:

1. **ZombieTypeController** receives it. `@RequestBody` turns the JSON into a `ZombieType` object,
   `@Valid` checks the annotations (`@NotBlank`, `@Positive`, ...). If a check fails, the method is not even called → 400.
2. **ZombieTypeService** applies the rules: "this `type_id` must not exist yet" → otherwise 409.
3. **ZombieTypeRepository** saves it in PostgreSQL. You never write SQL for this.
4. The controller returns the saved object; Spring turns it into JSON with status 201.

If something goes wrong, the service throws a `ResponseStatusException` and
**ApiExceptionHandler** turns it into `{"error": "..."}` with the right status code.

## 2. The files

| File | Job | If you know Servlets/JDBC, it is like... |
| --- | --- | --- |
| `ZombieType.java` | What is stored (`@Entity` = one table row) | a model class + `CREATE TABLE` |
| `Behaviour.java` | One rule inside a zombie type (`@Embeddable`, stored in a second table) | a child table |
| `ZombieTypeUpdate.java` | The PATCH body: all fields optional | a form object |
| `ZombieTypeRepository.java` | Database access, written by Spring | a DAO |
| `ZombieTypeService.java` | Business rules (404, 409, what PATCH changes) | service layer |
| `ZombieTypeController.java` | URLs and HTTP methods | `HttpServlet.doGet/doPost` |
| `ApiExceptionHandler.java` | Same error JSON everywhere | a try/catch around every servlet |

## 3. Things that look like magic

- **JSON names:** Java uses `typeId`, JSON uses `type_id`. One line in `application.properties`
  (`SNAKE_CASE`) converts between them.
- **The repository is empty:** `JpaRepository<ZombieType, String>` already has `save`, `findById`,
  `findAll`, `existsById`, `deleteById`. `String` is the type of the ID.
- **Tables are created automatically:** `spring.jpa.hibernate.ddl-auto=update` creates them on the
  first start and keeps the data afterwards.
- **Constructor injection:** you never write `new ZombieTypeService(...)`. Spring creates the
  repository, the service and the controller and passes each one into the next.
- **PATCH:** in `ZombieTypeUpdate` every field may be `null`. The service only copies non-null fields,
  so `{"health": 95}` changes only the health.

## 4. Where the database comes from

The root `docker-compose.yml` starts two containers for this service:

- `zombie-db` — PostgreSQL. Its files are in the volume `zombie-db-data`, so they survive restarts.
- `zombie-service` — this app. It gets `DB_URL`, `DB_USER`, `DB_PASSWORD` as environment variables,
  and `application.properties` reads them with `${DB_URL}` etc.

The username and password come from the root `.env` file (`ZOMBIE_DB_USER`, `ZOMBIE_DB_PASSWORD`), which Git ignores.
Inside Docker, the app reaches the database with the hostname `zombie-db` (the service name).

The tests use the `test` profile (`src/test/resources/application-test.properties`), which swaps
PostgreSQL for an in-memory H2 database, so `./mvnw test` works without Docker.

## 5. Check yourself

1. Which annotation reads the JSON body? — `@RequestBody`
2. Which class decides that a duplicate ID returns 409? — the service
3. Why does the data survive `docker compose down`? — it is in a Docker volume
4. Where is the database password written in the code? — nowhere; it comes from `.env` through environment variables
5. What does PATCH with `{"behaviours": []}` do? — removes all behaviours
