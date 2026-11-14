/**
 * Synapse Support Analytics - Configuración del Frontend
 */
const CONFIG = {
  // En desarrollo local con Spring Boot en el puerto 8051
  API_BASE_URL: localStorage.getItem('SYNAPSE_API_URL') || 'http://localhost:8051/api',
  
  // Claves de almacenamiento local (session/local)
  STORAGE_TOKEN: 'synapse_jwt_token',
  STORAGE_USER: 'synapse_user_data',
  
  // Rutas de navegación
  ROUTES: {
    LOGIN: 'index.html',
    APP: 'app.html'
  },

  // Umbral y modelos RAG informativos
  RAG_METRICS: {
    SIMILARITY_METRIC: 'cosine',
    DEFAULT_MIN_SCORE: 0.70
  }
};

window.CONFIG = CONFIG;
