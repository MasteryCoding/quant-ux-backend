package com.qux.acl;

import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import com.qux.model.User;

/**
 * 
 * Events can only be written by everybody that can read the app
 * Uses AppAcl to check permissions from app.users
 *
 */
public class EventAcl extends AppAcl {

	public EventAcl(MongoClient client) {
		super(client);
	}

	@Override
	public void canCreate(User user, RoutingContext event,Handler<Boolean> handler) {
		// Use parent's canRead to check app.users
		super.canRead(user, event, handler);
	}

	@Override
	public void canRead(User user, RoutingContext event,Handler<Boolean> handler) {
		if(user.hasRole(User.USER)){
			// Use parent's canRead to check app.users
			super.canRead(user, event, handler);
		} else {
			handler.handle(false);
		}
		
	}
	@Override
	public void canWrite(User user, RoutingContext event,	Handler<Boolean> handler) {
		if(user.hasRole(User.USER)){
			// Use parent's canWrite to check app.users
			super.canWrite(user, event, handler);
		} else {
			handler.handle(false);
		}
	}
	@Override
	public void canDelete(User user, RoutingContext event,	Handler<Boolean> handler) {
		if(user.hasRole(User.USER)){
			// Use parent's canWrite to check app.users
			super.canWrite(user, event, handler);
		} else {
			handler.handle(false);
		}
	}
	
	

}
