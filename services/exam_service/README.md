# Exam Service

## Responsibility
Exams, attempts, grades, achievements. Emits ExamPassed event.

## What it owns
- Exams, attempts, grades, achievements

## What it does not own
- Player XP directly, world unlocks directly

## Communication
- REST + events (RabbitMQ); emits ExamPassed to World/Player.

## API Endpoints
```
GET  /exams                    -> 200 { "exams": [{"examId","title","minLevel"}] }
POST /exams/start              { "playerId","examId" } -> 200 { "attemptId","questions" }
POST /exams/submit             { "attemptId","answers" } -> 200 { "passed","score" }
GET  /exams/:id/achievements/:playerId -> 200 { "achievements" }
```

## Events
- ExamPassed: { "eventType","playerId","examId","timestamp","rewards" }

## Data Management
- Database: PostgreSQL
