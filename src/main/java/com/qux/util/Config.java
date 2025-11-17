package com.qux.util;

import io.vertx.core.json.JsonObject;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {

  private static final Logger logger = LoggerFactory.getLogger(Config.class);

  public static final String ENV_DEBUG = "QUX_DEBUG";

  public static final String ENV_HTTP_HOST = "QUX_HTTP_HOST";

  public static final String ENV_HTTP_PORT = "QUX_HTTP_PORT";

  public static final String ENV_MONGO_DB_NAME = "QUX_MONGO_DB_NAME";

  public static final String ENV_MONGO_TABLE_PREFIX = "QUX_MONGO_TABLE_PREFIX";

  public static final String ENV_MONGO_CONNECTION_STRING = "QUX_MONGO_CONNECTION_STRING";

  public static final String ENV_JWT_PASSWORD = "QUX_JWT_PASSWORD";

  public static final String ENV_IMAGE_FOLDER_USER = "QUX_IMAGE_FOLDER_USER";

  public static final String ENV_IMAGE_FOLDER_APPS = "QUX_IMAGE_FOLDER_APPS";

  public static final String ENV_AUTH_SERVICE = "QUX_AUTH_SERVICE";

  public static final String ENV_USER_ALLOW_SIGNUP = "QUX_USER_ALLOW_SIGNUP";

  public static final String ENV_USER_ALLOWED_DOMAINS = "QUX_USER_ALLOWED_DOMAINS";

  public static final String ENV_EXTERNAL_API_URL = "QUX_EXTERNAL_API_URL";

  public static final String ENV_MC_SECRET_KEY = "QUX_MC_SECRET_KEY";

  public static final String DEBUG = "debug";

  public static final String HTTP_HOST = "http.host";

  public static final String HTTP_PORT = "http.port";

  public static final String MONGO_DB_NAME = "mongo.db_name";

  public static final String MONGO_TABLE_PREFIX = "mongo.table_prefix";

  public static final String MONGO_CONNECTION_STRING = "mongo.connection_string";

  public static final String JWT_PASSWORD = "jwt.password";

  public static final String IMAGE_FOLDER_USER = "image.folder.user";

  public static final String IMAGE_FOLDER_APPS = "image.folder.apps";

  public static final String AUTH_SERVICE = "auth.service";

  public static final String USER_ALLOW_SIGNUP = "user.allowSignUp";

  public static final String USER_ALLOWED_DOMAINS = "user.allowedDomains";

  public static final String EXTERNAL_API_URL = "external.api.url";

  public static final String MC_SECRET_KEY = "mc.secret.key";

  public static boolean isFileSystem(JsonObject config) {
    return true;
  }

  public static String getHttpHost(JsonObject config) {
    return config.getString(HTTP_HOST);
  }

  public static String getUserAllowedDomains(JsonObject config) {
    return config.getString(USER_ALLOWED_DOMAINS);
  }

  public static boolean getUserSignUpAllowed(JsonObject config) {
    return config.getBoolean(USER_ALLOW_SIGNUP);
  }

  public static JsonObject getMongo(JsonObject config) {
    JsonObject mongoConfig = config.getJsonObject("mongo");
    if (mongoConfig == null) {
      mongoConfig = new JsonObject()
          .put("connection_string", config.getString(Config.MONGO_CONNECTION_STRING))
          .put("db_name", config.getString(Config.MONGO_DB_NAME));
    }

    return mongoConfig;
  }

  public static JsonObject setDefaults(JsonObject config) {
    JsonObject result = config.copy();
    if (!result.containsKey(HTTP_HOST)) {
      result.put(HTTP_HOST, "https://quant-ux.com");
    }
    if (!result.containsKey(USER_ALLOW_SIGNUP)) {
      result.put(USER_ALLOW_SIGNUP, true);
    }
    if (!result.containsKey(USER_ALLOWED_DOMAINS)) {
      result.put(USER_ALLOWED_DOMAINS, "*");
    }
    return result;
  }

  public static JsonObject mergeEnvIntoConfig(JsonObject config) {
    return mergeEnvIntoConfig(config, System.getenv());
  }

  public static JsonObject mergeEnvIntoConfig(JsonObject config, Map<String, String> env) {
    JsonObject result = config.copy();
    mergeDebug(env, result);
    mergeAuth(env, result);
    mergeHTTP(env, result);
    mergeMongo(env, result);
    mergeImage(env, result);
    mergeUser(env, result);
    return result;

  }

  private static void mergeDebug(Map<String, String> env, JsonObject result) {
    if (env.containsKey(ENV_DEBUG)) {
      logger.error("mergeDebug() > " + ENV_DEBUG + " > " + env.get(ENV_DEBUG));
      result.put(DEBUG, "true".equals(env.get(ENV_DEBUG)));
    }
  }

  private static void mergeUser(Map<String, String> env, JsonObject result) {
    if (env.containsKey(ENV_USER_ALLOWED_DOMAINS)) {
      logger.warn("mergeUser() > " + ENV_USER_ALLOWED_DOMAINS);
      result.put(USER_ALLOWED_DOMAINS, env.get(ENV_USER_ALLOWED_DOMAINS));
    }
    if (env.containsKey(ENV_USER_ALLOW_SIGNUP)) {
      logger.error("mergeUser() > " + ENV_USER_ALLOW_SIGNUP + " > " + env.get(ENV_USER_ALLOW_SIGNUP));
      result.put(USER_ALLOW_SIGNUP, !"false".equals(env.get(ENV_USER_ALLOW_SIGNUP)));
    }
    if (env.containsKey(ENV_EXTERNAL_API_URL)) {
      logger.warn("mergeUser() > " + ENV_EXTERNAL_API_URL);
      result.put(EXTERNAL_API_URL, env.get(ENV_EXTERNAL_API_URL));
    }
  }

  private static void mergeImage(Map<String, String> env, JsonObject result) {
    if (env.containsKey(ENV_IMAGE_FOLDER_USER)) {
      logger.warn("mergeImage() > " + ENV_IMAGE_FOLDER_USER);
      result.put(IMAGE_FOLDER_USER, env.get(ENV_IMAGE_FOLDER_USER));
    }
    if (env.containsKey(ENV_IMAGE_FOLDER_APPS)) {
      logger.warn("mergeImage() > " + ENV_IMAGE_FOLDER_APPS);
      result.put(IMAGE_FOLDER_APPS, env.get(ENV_IMAGE_FOLDER_APPS));
    }
  }

  private static void mergeAuth(Map<String, String> env, JsonObject result) {
    if (env.containsKey(ENV_AUTH_SERVICE)) {
      logger.warn("mergeAuth() > " + ENV_AUTH_SERVICE);
      result.put(AUTH_SERVICE, env.get(ENV_AUTH_SERVICE));
    }

    if (env.containsKey(ENV_JWT_PASSWORD)) {
      logger.warn("mergeAuth() > " + ENV_JWT_PASSWORD);
      result.put(JWT_PASSWORD, env.get(ENV_JWT_PASSWORD));
    }

    if (env.containsKey(ENV_MC_SECRET_KEY)) {
      logger.warn("mergeAuth() > " + ENV_MC_SECRET_KEY);
      result.put(MC_SECRET_KEY, env.get(ENV_MC_SECRET_KEY));
    }
  }

  private static void mergeMongo(Map<String, String> env, JsonObject result) {
    if (env.containsKey(ENV_MONGO_CONNECTION_STRING)) {
      logger.warn("mergeMongo() > " + ENV_MONGO_CONNECTION_STRING);
      result.put(MONGO_CONNECTION_STRING, env.get(ENV_MONGO_CONNECTION_STRING));
    }
    if (env.containsKey(ENV_MONGO_DB_NAME)) {
      logger.warn("mergeMongo() > " + ENV_MONGO_DB_NAME);
      result.put(MONGO_DB_NAME, env.get(ENV_MONGO_DB_NAME));
    }
    if (env.containsKey(ENV_MONGO_TABLE_PREFIX)) {
      logger.warn("mergeMongo() > " + ENV_MONGO_TABLE_PREFIX);
      result.put(MONGO_TABLE_PREFIX, env.get(ENV_MONGO_TABLE_PREFIX));
    }
  }

  private static void mergeHTTP(Map<String, String> env, JsonObject result) {
    if (env.containsKey(ENV_HTTP_HOST)) {
      logger.warn("mergeHTTP() > " + ENV_HTTP_HOST);
      result.put(HTTP_HOST, env.get(ENV_HTTP_HOST));
    }
    if (env.containsKey(ENV_HTTP_PORT)) {
      logger.warn("mergeHTTP() > " + ENV_HTTP_PORT);
      try {
        String port = env.get(ENV_HTTP_PORT);
        result.put(HTTP_PORT, Integer.parseInt(port));
      } catch (Exception e) {
        logger.error("Config.mergeHTTP() > Could not merge http.port from env: " + ENV_HTTP_PORT);
      }

    }
  }

}
