package com.openseedbox.jobs.startup.db;


import play.Logger;
import play.jobs.Job;
import com.openseedbox.models.User;
import play.Play;
import play.jobs.OnApplicationStart;
import play.modules.siena.SienaFixtures;

@OnApplicationStart
public class FixturesLoadJob extends Job {
	private static final String FIXTURE_NAME = "test-data.yaml";
	@Override
	public void doJob() throws Exception {
		if (!Play.mode.isProd() && User.count() == 0) {
			SienaFixtures.loadModels(FIXTURE_NAME);
			Logger.trace("Fixture file %s loaded!", FIXTURE_NAME);
		}
	}
}
