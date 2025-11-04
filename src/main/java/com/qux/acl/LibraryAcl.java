package com.qux.acl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qux.model.Library;
import com.qux.model.Model;
import com.qux.model.User;
import com.qux.util.DB;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class LibraryAcl extends MongoAcl implements Acl {

  private Logger logger = LoggerFactory.getLogger(LibraryAcl.class);

  private final String library_db;

  public LibraryAcl(MongoClient client) {
    super(client);
    this.library_db = DB.getTable(Library.class);
  }

  public void canCreate(User user, RoutingContext event, Handler<Boolean> handler) {
    if (user.hasRole(User.USER)) {
      handler.handle(true);
    } else {
      handler.handle(false);
    }
  }

  @Override
  public void canRead(User user, RoutingContext event, Handler<Boolean> handler) {

    String libID = getId(event);

    logger.debug("canRead() > " + libID);

    /**
     * First check if user has permission in library.users, otherwise check if
     * library is public
     */
    client.findOne(library_db, Model.findById(libID), null, res -> {
      if (res.succeeded()) {
        JsonObject library = res.result();
        if (library != null) {
          // Check if user has permission in library.users
          if (library.containsKey("users")) {
            JsonObject users = library.getJsonObject("users");
            if (users.containsKey(user.getId())) {
              int permission = users.getInteger(user.getId());
              if (permission >= Acl.READ) {
                handler.handle(true);
                return;
              }
            }
          }
          // Check if library is public
          if (library.getBoolean("isPublic", false)) {
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
    client.findOne(library_db, Model.findById(id), null, res -> {
      if (res.succeeded()) {
        JsonObject library = res.result();
        if (library != null && library.containsKey("users")) {
          JsonObject users = library.getJsonObject("users");
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
    client.findOne(library_db, Model.findById(id), null, res -> {
      if (res.succeeded()) {
        JsonObject library = res.result();
        if (library != null && library.containsKey("users")) {
          JsonObject users = library.getJsonObject("users");
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

  protected String getId(RoutingContext event) {
    return event.request().getParam("libID");
  }

}
