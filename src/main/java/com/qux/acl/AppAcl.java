package com.qux.acl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.App;
import com.qux.model.Model;
import com.qux.model.User;
import com.qux.util.DB;

public class AppAcl extends MongoAcl implements Acl {

  private final String app_db;

  private Logger logger = LoggerFactory.getLogger(AppAcl.class);

  public AppAcl(MongoClient client) {
    this(client, "appID");
  }

  public AppAcl(MongoClient client, String id) {
    super(client, id);
    this.app_db = DB.getTable(App.class);
  }

  @Override
  public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
    if (user.hasRole(User.USER)) {
      handler.handle(true);
    } else {
      handler.handle(false);
    }
  }

  @Override
  public void canRead(User user, RoutingContext event, Handler<Boolean> handler) {
    String appID = getId(event);
    logger.debug("canRead() > " + appID);

    /**
     * First check if user has permission in app.users, otherwise check if app is
     * public
     */
    client.findOne(app_db, Model.findById(appID), null, res -> {
      if (res.succeeded()) {
        JsonObject app = res.result();
        if (app != null) {
          // Check if user has permission in app.users
          if (app.containsKey("users")) {
            JsonObject users = app.getJsonObject("users");
            if (users.containsKey(user.getId())) {
              int permission = users.getInteger(user.getId());
              if (permission >= Acl.READ) {
                handler.handle(true);
                return;
              }
            }
          }
          // Check if app is public
          if (app.getBoolean("isPublic", false)) {
            handler.handle(true);
            return;
          }
          handler.handle(false);
        } else {
          handler.handle(false);
        }
      } else {
        logger.error("canRead() > Error: " + res.cause());
        handler.handle(false);
      }
    });
  }

  @Override
  public void canWrite(User user, RoutingContext event, Handler<Boolean> handler) {
    String id = getId(event);
    client.findOne(app_db, Model.findById(id), null, res -> {
      if (res.succeeded()) {
        JsonObject app = res.result();
        if (app != null && app.containsKey("users")) {
          JsonObject users = app.getJsonObject("users");
          if (users.containsKey(user.getId())) {
            int permission = users.getInteger(user.getId());
            handler.handle(permission >= Acl.WRITE);
            return;
          }
        }
        handler.handle(false);
      } else {
        logger.error("canWrite() > Error: " + res.cause());
        handler.handle(false);
      }
    });
  }

  @Override
  public void canDelete(User user, RoutingContext event, Handler<Boolean> handler) {
    String id = getId(event);
    client.findOne(app_db, Model.findById(id), null, res -> {
      if (res.succeeded()) {
        JsonObject app = res.result();
        if (app != null && app.containsKey("users")) {
          JsonObject users = app.getJsonObject("users");
          if (users.containsKey(user.getId())) {
            int permission = users.getInteger(user.getId());
            handler.handle(permission >= Acl.OWNER);
            return;
          }
        }
        handler.handle(false);
      } else {
        logger.error("canDelete() > Error: " + res.cause());
        handler.handle(false);
      }
    });
  }

}
