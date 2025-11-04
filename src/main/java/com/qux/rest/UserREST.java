package com.qux.rest;

import java.util.ArrayList;
import java.util.List;
import com.qux.acl.UserAcl;
import com.qux.auth.ITokenService;
import com.qux.blob.IBlobService;
import com.qux.model.AppEvent;
import com.qux.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.util.rest.MongoREST;
import com.qux.util.Util;
import com.qux.util.Config;
import com.qux.validation.UserValidator;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;

public class UserREST extends MongoREST {

  // private final String imageFolder;

  private long imageSize = 1024 * 1024;

  private final Logger logger = LoggerFactory.getLogger(UserREST.class);

  private final IBlobService blobService;

  private WebClient webClient;
  private String externalApiUrl;

  public UserREST(ITokenService tokenService, IBlobService blobService, MongoClient db, JsonObject conf, Vertx vertx) {
    super(tokenService, db, User.class);
    this.blobService = blobService;
    this.imageSize = conf.getLong("image.size");
    this.webClient = WebClient.create(vertx);
    this.externalApiUrl = conf.getString(Config.EXTERNAL_API_URL);
    setACL(new UserAcl(db));
    setValidator(new UserValidator(db));
    setPartialUpdate(true);
  }

  public Handler<RoutingContext> current() {
    return new Handler<RoutingContext>() {
      @Override
      public void handle(RoutingContext event) {
        current(event);
      }
    };
  }

  public void current(RoutingContext event) {
    logger.info("current() > enter");
    User user = getUser(event);
    JsonObject json = mapper.toVertx(user);
    event.response().end(cleanJson(json).encode());
  }

  public Handler<RoutingContext> login() {
    return new Handler<RoutingContext>() {
      @Override
      public void handle(RoutingContext event) {
        login(event);
      }
    };
  }

  protected void login(RoutingContext event) {
    this.logger.info("login() > enter");
    JsonObject login = event.getBodyAsJson();
    if (login.containsKey("email") && login.containsKey("password")) {
      this.mongo.findOne(this.table, User.findByEmail(login.getString("email")), null, res -> {
        if (res.succeeded()) {
          JsonObject user = res.result();
          if (user != null) {
            checkPassword(event, login, user);
          } else {
            error("login", "No user with mail :" + login.getString("email"));
            returnError(event, "user.login.fail");
          }
        } else {
          returnError(event, "user.login.fail");
        }
      });
    } else {
      returnError(event, "user.login.fail");
    }
  }

  public void checkPassword(RoutingContext event, JsonObject login, JsonObject user) {
    if (Util.matchPassword(login.getString("password"), user.getString("password"))) {
      setLoginUser(event, user);
    } else {
      updateFailedLogins(user);
      AppEvent.send(event, user.getString("email"), AppEvent.TYPE_USER_LOGIN_ERROR);
      logger.error("error() > *ATTENTION* Wrong login for " + user.getString("email"));
      returnError(event, "user.login.fail");
    }
  }

  public void setLoginUser(RoutingContext event, JsonObject user) {

    int loginCount = 0;
    if (user.containsKey("loginCount")) {
      loginCount = user.getInteger("loginCount");
    }
    loginCount++;
    user.put("loginCount", loginCount);
    user.put("lastlogin", System.currentTimeMillis());

    mongo.save(table, user, write -> {
      if (!write.succeeded()) {
        logger.error("login() > Could not update lastLogin");
      }
    });
    AppEvent.send(event, user.getString("email"), AppEvent.TYPE_USER_LOGIN);
    String token = this.getTokenService().getToken(user);
    user.put("token", token);
    event.response().end(cleanJson(user.copy()).encode());
  }

  private void updateFailedLogins(JsonObject user) {
    if (!user.containsKey("failedLoginAttempts")) {
      user.put("failedLoginAttempts", 0);
    }
    user.put("failedLoginAttempts", user.getInteger("failedLoginAttempts") + 1);
    mongo.save(table, user, write -> {
      if (!write.succeeded()) {
        logger.error("login() > Could not store failedLoginAttempts");
      }
    });
  }

  public Handler<RoutingContext> logout() {
    return new Handler<RoutingContext>() {
      @Override
      public void handle(RoutingContext event) {
        logout(event);
      }
    };
  }

  public void logout(RoutingContext event) {
    User user = getUser(event);
    AppEvent.send(event, user.getEmail(), AppEvent.TYPE_USER_LOGOUT);
    returnOk(event, "user.logged.out");
  }

  public void afterDelete(RoutingContext event, String id) {
    logger.info("afterDelete() > " + id);
    // Note: User permissions are now stored in app.users, so no cleanup needed
  }

  public void createExternalIfNotExists(RoutingContext event) {
    logger.info("createExternalIfNotExists() > ");

    JsonObject json = event.getBodyAsJson();
    if (!json.containsKey("id")) {
      logger.error("createExternalIfNotExists() > No id");
      returnError(event, 400);
    }
    if (!json.containsKey("email")) {
      logger.error("createExternalIfNotExists() > No email");
      returnError(event, 400);
    }
    if (!json.containsKey("name")) {
      logger.error("createExternalIfNotExists() > No name");
      returnError(event, 400);
    }

    String id = json.getString("id");
    this.mongo.findOne(this.table, User.findById(id), null, res -> {
      if (res.succeeded()) {
        JsonObject result = res.result();
        if (result != null) {
          logger.info("createExternalIfNotExists() > Found user");
          result.put("id", id);
          result.remove("_id");
          cleanJson(result);
          returnJson(event, result);
        } else {
          logger.info("createExternalIfNotExists() > Create user");
          insertExternal(event, json, id);
        }
      } else {
        returnError(event, 401);
      }
    });
  }

  private void insertExternal(RoutingContext event, JsonObject json, String id) {
    logger.info("insertExternal() > Create user : " + id);

    json.remove("id");
    json.put("_id", id);

    json.put("external", true);
    json.put("created", System.currentTimeMillis());
    json.put("lastUpdate", System.currentTimeMillis());
    json.put("email", json.getString("email").toLowerCase());
    json.put("external", true);
    json.put("role", User.USER);
    json.put("password", Util.getRandomString());
    json.put("acceptedGDPR", true);

    this.mongo.insert(this.table, json, res -> {

      if (res.succeeded()) {
        this.logger.error("insertExternal() > Created user");

        json.put("id", id);
        cleanJson(json);
        returnJson(event, json);

      } else {
        this.logger.error("insertExternal() > could not save user", res.cause());
        returnError(event, 401);
      }
    });
  }

  public void exchangeToken(RoutingContext event) {
    logger.info("exchangeToken() > enter");

    // Check if external API is configured
    if (externalApiUrl == null || externalApiUrl.isEmpty()) {
      logger.error("exchangeToken() > External API URL not configured");
      returnError(event, 500);
      return;
    }

    // Get the external token from Authorization header
    String authHeader = event.request().getHeader("Authorization");
    if (authHeader == null || authHeader.isEmpty()) {
      logger.error("exchangeToken() > No Authorization header");
      returnError(event, 401);
      return;
    }

    // Extract token (could be Bearer token or just the token)
    String externalToken = authHeader;
    if (authHeader.startsWith("Bearer ")) {
      externalToken = authHeader.substring(7);
    }

    // Call external API to get user info
    webClient.getAbs(externalApiUrl + "/v3/user/me")
        .putHeader("Authorization", externalToken)
        .send(apiRes -> {
          if (apiRes.failed()) {
            logger.error("exchangeToken() > Failed to call external API", apiRes.cause());
            returnError(event, 502);
            return;
          }

          if (apiRes.result().statusCode() != 200) {
            logger.error("exchangeToken() > External API returned status: " + apiRes.result().statusCode());
            returnError(event, 401);
            return;
          }

          try {
            JsonObject externalUser = apiRes.result().bodyAsJsonObject();

            // Validate required fields
            if (!externalUser.containsKey("id")) {
              logger.error("exchangeToken() > External user missing id");
              returnError(event, 400);
              return;
            }
            if (!externalUser.containsKey("username")) {
              logger.error("exchangeToken() > External user missing username");
              returnError(event, 400);
              return;
            }

            // Map external user to Quant-UX user format
            String externalUserId = externalUser.getString("id");
            String email = externalUser.getString("username");
            String firstName = externalUser.getString("firstName", "");
            String lastName = externalUser.getString("lastName", "");

            // Ensure user exists in Quant-UX (idempotent)
            JsonObject quantUXUserJson = new JsonObject()
                .put("id", externalUserId)
                .put("email", email)
                .put("name", firstName)
                .put("lastname", lastName);

            // Check if user already exists
            this.mongo.findOne(this.table, User.findById(externalUserId), null, findRes -> {
              if (findRes.failed()) {
                logger.error("exchangeToken() > Failed to query user", findRes.cause());
                returnError(event, 500);
                return;
              }

              JsonObject existingUser = findRes.result();
              if (existingUser != null) {
                // User exists, generate token
                logger.info("exchangeToken() > User exists, generating token");
                existingUser.put("id", externalUserId);
                existingUser.remove("_id");
                String token = this.getTokenService().getToken(existingUser);
                JsonObject response = cleanJson(existingUser.copy());
                response.put("token", token);
                returnJson(event, response);
              } else {
                // User doesn't exist, create it
                logger.info("exchangeToken() > Creating new user");
                insertExternalForTokenExchange(event, quantUXUserJson, externalUserId);
              }
            });
          } catch (Exception e) {
            logger.error("exchangeToken() > Error processing external user", e);
            returnError(event, 500);
          }
        });
  }

  private void insertExternalForTokenExchange(RoutingContext event, JsonObject json, String id) {
    json.remove("id");
    json.put("_id", id);

    json.put("external", true);
    json.put("created", System.currentTimeMillis());
    json.put("lastUpdate", System.currentTimeMillis());
    json.put("email", json.getString("email").toLowerCase());
    json.put("role", User.USER);
    json.put("password", Util.getRandomString());
    json.put("acceptedGDPR", true);

    this.mongo.insert(this.table, json, res -> {
      if (res.succeeded()) {
        logger.info("insertExternalForTokenExchange() > Created user: " + id);

        json.put("id", id);
        String token = this.getTokenService().getToken(json);
        JsonObject response = cleanJson(json.copy());
        response.put("token", token);
        returnJson(event, response);
      } else {
        logger.error("insertExternalForTokenExchange() > Could not save user", res.cause());
        returnError(event, 500);
      }
    });
  }

  public void update(RoutingContext event, String id, JsonObject json) {

    if (json.containsKey("password")) {
      json.put("password", Util.hashPassword(json.getString("password")));
    }

    if (json.containsKey("role")) {
      logger.error("update() > User " + getUser(event) + " tried to set role!");
      json.remove("role");
    }

    if (json.containsKey("paidUntil")) {
      logger.error("update() > User " + getUser(event) + " tried to set paidUntil!");
      logger.error("UserRest.update() " + getUser(event) + " tried to set paidUntil");
      json.remove("paidUntil");
    }

    if (json.containsKey("domain")) {
      logger.error("update() > User " + getUser(event) + " tried to set domain!");
      json.remove("domain");
    }

    if (json.containsKey("status")) {
      logger.error("update() > User " + getUser(event) + " tried to set status!");
      json.remove("status");
    }

    if (json.containsKey("has")) {
      logger.error("update() > User " + getUser(event) + " tried to set has!");
      json.remove("has");
    }

    checkEmailUpdate(event, id, json);
  }

  private void checkEmailUpdate(RoutingContext event, String id, JsonObject json) {
    if (json.containsKey("email")) {
      logger.info("checkEmailUpdate() > User " + getUser(event) + " changed email");
      String email = json.getString("email");
      this.mongo.count(table, User.findByEmail(email), res -> {
        if (res.succeeded() && res.result() == 0) {
          logger.warn("checkEmailUpdate() > User " + getUser(event) + " changed email");
          AppEvent.send(event, json.getString("email"), AppEvent.TYPE_USER_CHANGE_EMAIL);
          super.update(event, id, json);
        } else {
          logger.warn("checkEmailUpdate() > User " + getUser(event) + " tried used mail!");
          returnError(event, "user.update.email.taken");
        }
      });
    } else {
      super.update(event, id, json);
    }
  }

  protected void afterUpdate(RoutingContext event, String id, JsonObject json) {
  }

  public Handler<RoutingContext> setImage() {
    return new Handler<RoutingContext>() {
      @Override
      public void handle(RoutingContext event) {
        setImage(event);
      }
    };
  }

  public void setImage(RoutingContext event) {
    String id = event.request().getParam("id");
    if (this.acl != null) {
      this.acl.canWrite(getUser(event), event, allowed -> {
        if (allowed) {
          setImage(event, id);
        } else {
          returnError(event, 405);
        }
      });
    } else {
      this.setImage(event, id);
    }
  }

  public void setImage(RoutingContext event, String id) {
    List<FileUpload> files = new ArrayList<FileUpload>(event.fileUploads());
    if (files.size() == 1) {
      FileUpload file = files.get(0);
      setImage(event, id, file);
    } else {
      FileSystem fs = event.vertx().fileSystem();
      for (FileUpload file : files) {
        fs.delete(file.uploadedFileName(), res -> {

        });
      }
      returnError(event, 404);
    }
  }

  private void setImage(RoutingContext event, String id, FileUpload file) {
    if (checkImage(file)) {
      String userFolder = this.blobService.createFolder(event, id);
      String imageID = System.currentTimeMillis() + "";
      String type = Util.getFileType(file.fileName());
      String image = imageID + "." + type;
      String dest = userFolder + "/" + image;
      this.blobService.setBlob(event, file.uploadedFileName(), dest, uploadResult -> {
        if (uploadResult) {
          onUserImageUploaded(event, id, image);
        } else {
          returnError(event, "user.image.error2");
        }
      });
    } else {
      FileSystem fs = event.vertx().fileSystem();
      fs.delete(file.uploadedFileName(), res -> {
        if (!res.succeeded()) {
          error("setImage", "Could not delete " + file.uploadedFileName());
        }
      });
      returnError(event, "user.image.wrong");
    }
  }

  private void onUserImageUploaded(RoutingContext event, String id, String image) {
    User user = getUser(event);
    user.setImage(image);
    JsonObject json = new JsonObject().put("image", image);
    update(event, id, json);
  }

  private boolean checkImage(FileUpload file) {
    return file.size() < this.imageSize;
  }

  public Handler<RoutingContext> deleteImage() {
    return new Handler<RoutingContext>() {
      @Override
      public void handle(RoutingContext event) {
        deleteImage(event);
      }
    };
  }

  public void deleteImage(RoutingContext event) {
    String id = event.request().getParam("id");
    String image = event.request().getParam("image");
    if (this.acl != null) {
      this.acl.canWrite(getUser(event), event, allowed -> {
        if (allowed) {
          this.mongo.findOne(table, User.findById(id), null, res -> {
            if (res.succeeded() && res.result() != null) {

              User user = getUser(event);
              user.setImage(null);

              JsonObject json = res.result();
              json.remove("image");

              JsonObject query = new JsonObject()
                  .put("$unset", new JsonObject().put("image", ""));

              mongo.updateCollection(table, User.findById(id), query, updated -> {
                if (updated.succeeded()) {
                  returnJson(event, cleanJson(json));
                } else {
                  returnError(event, 405);
                  error("deleteImage", "Could not update user");
                }
              });
              this.blobService.deleteFile(event, id, image, deleteResult -> {
                if (!deleteResult) {
                  error("deleteImage", "Could not delete image " + image);
                }
              });
            } else {
              error("deleteImage", "Could not load user");
              returnError(event, 405);
            }
          });
        } else {
          error("deleteImage", "The user " + getUser(event) + " tried to delete an image ");
          returnError(event, 405);
        }

      });
    } else {
      getImage(event, id, image);
    }
  }

  public Handler<RoutingContext> getImage() {
    return new Handler<RoutingContext>() {
      @Override
      public void handle(RoutingContext event) {
        getImage(event);
      }
    };
  }

  public void getImage(RoutingContext event) {

    String id = event.request().getParam("id");
    String image = event.request().getParam("image");

    if (this.acl != null) {
      this.acl.canRead(getUser(event), event, allowed -> {
        if (allowed) {
          getImage(event, id, image);
        } else {
          returnError(event, 404);
        }
      });
    } else {
      getImage(event, id, image);
    }
  }

  public void getImage(RoutingContext event, String userID, String image) {
    this.blobService.getBlob(event, userID, image);
  }

  protected JsonObject cleanJson(JsonObject user) {
    user.remove("password");
    return super.cleanJson(user);
  }

}
