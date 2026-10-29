// ============================================================================
// CONFIGURATION
// Everything that can change between "my laptop" and "Docker" lives here.
// ============================================================================

export function loadConfig() {
  return {
    port: Number(process.env.PORT || 8080),

    // Lab 3: where the Service Discovery is (in Docker: http://service-discovery:8761).
    // When it is set, the gateway asks it which instances exist and ignores the *_SERVICE_URL values below.
    serviceDiscoveryUrl: process.env.SERVICE_DISCOVERY_URL || '',
    // The gateway's own address, so it can register itself too (in Docker: http://gateway:8080).
    selfUrl: process.env.SELF_URL || '',

    // Without a Service Discovery (on your laptop, in the tests): one fixed address per service.
    playerServiceUrl: process.env.PLAYER_SERVICE_URL || 'http://localhost:8081',
    gameServiceUrl: process.env.GAME_SERVICE_URL || 'http://localhost:8082',
    zombieServiceUrl: process.env.ZOMBIE_SERVICE_URL || 'http://localhost:8083',
    resourceServiceUrl: process.env.RESOURCE_SERVICE_URL || 'http://localhost:8084',
    baseServiceUrl: process.env.BASE_SERVICE_URL || 'http://localhost:5007',
    craftingServiceUrl: process.env.CRAFTING_SERVICE_URL || 'http://localhost:5008',

    // If a service doesn't answer in this time, the gateway replies 504 (the "task timeout limit").
    // The circuit breaker counts errors in a window of timeoutMs * 3.5.
    timeoutMs: Number(process.env.UPSTREAM_TIMEOUT_MS || 10000),
  };
}

// ============================================================================
// ROUTING TABLE
// "Requests whose path starts with PREFIX go to SERVICE."
// healthPath = where GET /health asks the service if it is alive
//   (Spring Boot services: /actuator/health, Flask services: /health).
// To add a new microservice later, add one line here.
// ============================================================================

const SPRING = '/actuator/health';
const FLASK = '/health';

export const ROUTES = [
  { prefix: '/api/players', service: 'player-service', healthPath: SPRING },
  { prefix: '/api/trades', service: 'player-service', healthPath: SPRING },

  { prefix: '/api/game', service: 'game-service', healthPath: SPRING },
  { prefix: '/api/lobbies', service: 'game-service', healthPath: SPRING },
  { prefix: '/api/sessions', service: 'game-service', healthPath: SPRING },

  { prefix: '/api/zombies', service: 'zombie-service', healthPath: SPRING },
  { prefix: '/api/resources', service: 'resource-service', healthPath: SPRING },

  { prefix: '/api/bases', service: 'base-service', healthPath: FLASK },
  { prefix: '/api/crafting', service: 'crafting-service', healthPath: FLASK },
];

/** The fixed addresses, used only when there is no Service Discovery. */
export function fixedServiceUrls(config) {
  return {
    'player-service': config.playerServiceUrl,
    'game-service': config.gameServiceUrl,
    'zombie-service': config.zombieServiceUrl,
    'resource-service': config.resourceServiceUrl,
    'base-service': config.baseServiceUrl,
    'crafting-service': config.craftingServiceUrl,
  };
}
