// Prueba de carga de la API con k6:  k6 run test.js
// Por defecto contra http://localhost:8090 con los datos demo; otra URL con  k6 run -e BASE_URL=... test.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8090';

export const options = {
  // Rampa: subir, mantener (aquí está la medida) y bajar, para no medir el arranque de la JVM
  stages: [
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
export function setup() {
  const res = http.post(`${BASE}/api/v1/auth/login`,
    JSON.stringify({ correo: 'admin@demo.com', password: 'admin123' }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login responde 200': (r) => r.status === 200 });
  return { token: res.json('token') };
}

// Lo que hace cada usuario virtual, en bucle: abrir el inicio, la lista y el diagrama de un proceso
export default function (datos) {
  const params = { headers: { Authorization: `Bearer ${datos.token}` } };

  const resumen = http.get(`${BASE}/api/v1/procesos/resumen`, params);
  check(resumen, { 'resumen responde 200': (r) => r.status === 200 });

  const lista = http.get(`${BASE}/api/v1/procesos?page=0&size=10&sort=modificadoEn,desc`, params);
  check(lista, {
    'lista responde 200': (r) => r.status === 200,
    'la lista trae procesos': (r) => r.json('totalElements') > 0,
  });

  const diagrama = http.get(`${BASE}/api/v1/procesos/1/diagrama`, params);
  check(diagrama, { 'diagrama responde 200': (r) => r.status === 200 });

  // Pausa entre acciones del mismo usuario: sin ella sería una prueba de estrés, no de carga
  sleep(1);
}
