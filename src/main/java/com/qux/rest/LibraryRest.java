package com.qux.rest;

import java.util.List;

import com.qux.acl.Acl;
import com.qux.acl.LibraryAcl;
import com.qux.auth.ITokenService;
import com.qux.model.App;
import com.qux.model.AppEvent;
import com.qux.model.Library;
import com.qux.model.Model;
import com.qux.util.DB;
import com.qux.util.rest.MongoREST;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

public class LibraryRest extends MongoREST {

  private final String library_db;

  public LibraryRest(ITokenService tokenService, MongoClient db) {
    super(tokenService, db, Library.class);
    this.setACL(new LibraryAcl(db));
    this.setPartialUpdate(false);
    this.setIdParameter("libID");
    this.library_db = DB.getTable(Library.class);
  }

  public Handler<RoutingContext> findByUser() {
    return new Handler<RoutingContext>() {
      @Override
      public void handle(RoutingContext event) {
        findByUser(event);
      }
    };
  }

  private void findByUser(RoutingContext event) {
    logger.debug("findByUser() > enter");
    /**
     * Query libraries where users contains the userID
     */
    long start = System.currentTimeMillis();
    String userID = getUser(event).getId();

    // Query libraries where users contains the userID
    JsonObject query = new JsonObject()
        .put("users." + userID, new JsonObject().put("$exists", true));

    mongo.find(library_db, query, res -> {

      if (res.succeeded()) {
        List<JsonObject> libraries = res.result();
        JsonArray libIDs = new JsonArray();

        for (JsonObject lib : libraries) {
          String libID = lib.getString("_id");
          if (libID != null) {
            libIDs.add(libID);
          }
        }
        long end = System.currentTimeMillis();
        logger.info("findByUser() > exit > library_db: " + (end - start));
        this.logMetric(this.getClass(), "findByUser", (end - start));
        findByIds(event, libIDs);
      } else {
        logger.error("findByUser() > Mongo Error " + res.cause().getMessage());
        returnError(event, 404);
      }
    });
  }

  private void findByIds(RoutingContext event, JsonArray appIDs) {
    logger.debug("findByIds() > enter " + appIDs);
    mongo.find(library_db, Model.findByIDS(appIDs), appRes -> {

      if (appRes.succeeded()) {
        long dbDone = System.currentTimeMillis();

        List<JsonObject> apps = appRes.result();
        JsonArray result = new JsonArray();
        for (JsonObject app : apps) {
          /**
           * Sometimes the app might be marked for deletion, but it is still not deleted!
           */
          if (!App.isDeleted(app)) {
            /**
             * Filter out all not needed widgets etc to speed up loading
             */
            app = cleanJson(app);
            result.add(app);
          } else {
            logger.info("findByUser() > app " + app);
          }
        }
        event.response().end(result.encode());
        long end = System.currentTimeMillis();
        this.logMetric(this.getClass(), "findByUser[preview]", (end - dbDone), result.size());
      } else {
        logger.error("findByUser() > Mongo Error : " + appRes.cause().getMessage());
        returnError(event, 404);
      }
    });
  }

  /********************************************************************************************
   * create
   ********************************************************************************************/

  protected void beforeCreate(RoutingContext event, JsonObject json) {
    json.put("created", System.currentTimeMillis());
  }

  protected void afterCreate(RoutingContext event, JsonObject app) {
    logger.info("afterCreate() > enter " + app.getString("_id"));

    String libID = app.getString("_id");
    String userID = getUser(event).getId();

    // Set owner permission in library.users
    JsonObject users = new JsonObject();
    users.put(userID, Acl.OWNER);
    JsonObject update = new JsonObject().put("$set", new JsonObject().put("users", users));

    mongo.updateCollection(table, Model.findById(libID), update, ownerUpdated -> {
      if (ownerUpdated.succeeded()) {
        logger.info("afterCreate() > Owner added > ");
      } else {
        logger.error("afterCreate() Could not add owner");
      }
    });
    AppEvent.send(event, getUser(event).getEmail(), AppEvent.TYPE_LIB_CREATE, app.getString("_id"));
  }

  /********************************************************************************************
   * create
   ********************************************************************************************/

  public void delete(RoutingContext event, String appID) {
    log("delete", "Delete " + appID);

    mongo.removeDocuments(library_db, Model.findById(appID), res -> {
      if (res.succeeded()) {
        returnOk(event, table + ".delete.success");
      } else {
        log("delete", "Cannot set isDeletedFlag");
        logger.error("AppREST.delete() > Could set isDeletedFLag");
        returnError(event, table + ".delete.error");
      }
    });
  }

}
