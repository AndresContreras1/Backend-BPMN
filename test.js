// Prueba de carga de la API con k6:  k6 run test.js
// Por defecto contra http://localhost:8090 con los datos demo; otra URL con  k6 run -e BASE_URL=... test.js
// Modulos de k6: http hace las peticiones, check valida respuestas y sleep pausa
import http from 'k6/http';
import { check, sleep } from 'k6';

// URL base de la API; se puede cambiar con la variable BASE_URL
const BASE = __ENV.BASE_URL || 'http://localhost:8090';

// Configuracion de la prueba: cuantos usuarios virtuales (VUs) y que resultados se aceptan
export const options = {
  // Rampa: subir, mantener (aquí está la medida) y bajar, para no medir el arranque de la JVM
  stages: [
    // Cada etapa lleva el numero de usuarios virtuales al valor "target" durante "duration": sube a 20, se mantiene y baja a 0
    { duration: '15s', target: 20 },
    { duration: '30s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  // Si no se cumplen, k6 termina con error: la prueba falla sola
  thresholds: {
    http_req_failed: ['rate<0.01'],   // menos del 1 % de errores
    http_req_duration: ['p(95)<500'], // el 95 % de las respuestas en menos de 500 ms
    checks: ['rate>0.99'],
  },
};

// Corre una sola vez antes de la prueba: el token del login lo usan todos los usuarios virtuales.
// El login tiene límite de intentos por IP, por eso no se repite en cada iteración.
// setup devuelve datos que k6 pasa a la funcion principal de cada usuario virtual
export function setup() {
  // Inicio de sesion con el administrador de los datos demo
  const res = http.post(`${BASE}/api/v1/auth/login`,
    JSON.stringify({ correo: 'admin@demo.com', password: 'admin123' }),
    { headers: { 'Content-Type': 'application/json' } });
  // check anota si la respuesta cumple la condicion; no detiene la prueba, pero alimenta el umbral "checks"
  check(res, { 'login responde 200': (r) => r.status === 200 });
  // El token JWT queda disponible para todos los usuarios virtuales
  return { token: res.json('token') };
}

// Lo que hace cada usuario virtual, en bucle: abrir el inicio, la lista y el diagrama de un proceso
// Se ejecuta una y otra vez por cada usuario virtual mientras dura la prueba
export default function (datos) {
  // Todas las peticiones llevan el token en el header Authorization
  const params = { headers: { Authorization: `Bearer ${datos.token}` } };

  // 1. Resumen que muestra la pagina de inicio
  const resumen = http.get(`${BASE}/api/v1/procesos/resumen`, params);
  check(resumen, { 'resumen responde 200': (r) => r.status === 200 });

  // 2. Primera pagina de procesos, ordenada por ultima modificacion
  const lista = http.get(`${BASE}/api/v1/procesos?page=0&size=10&sort=modificadoEn,desc`, params);
  check(lista, {
    'lista responde 200': (r) => r.status === 200,
    'la lista trae procesos': (r) => r.json('totalElements') > 0,
  });

  // 3. Diagrama del proceso 1 (el de vacaciones de los datos demo)
  const diagrama = http.get(`${BASE}/api/v1/procesos/1/diagrama`, params);
  check(diagrama, { 'diagrama responde 200': (r) => r.status === 200 });

  // Pausa entre acciones del mismo usuario: sin ella sería una prueba de estrés, no de carga
  sleep(1);
}
