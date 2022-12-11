package com.openseedbox.jobs;

import com.openseedbox.code.Util;
import com.openseedbox.models.EventBase;

import java.util.Date;

import org.apache.commons.lang.exception.ExceptionUtils;
import play.Logger;
import play.Play;
import play.Play.Mode;

public abstract class LoggedJob<T extends EventBase> extends GenericJob {

	@Override
	protected GenericJobResult runJob() throws Exception {				
		T eb = getEvent();
		eb.setStartDate(new Date());
		long startMillis = System.currentTimeMillis();
		GenericJobResult res = super.runJob();
		long endMillis = System.currentTimeMillis();		
		eb.setDurationMilliseconds(endMillis - startMillis);
		eb.setCompletionDate(new Date());
		if (res.hasError()) {
			eb.setSuccessful(false);			
			eb.setStackTrace(ExceptionUtils.getStackTrace(res.getError()));
			if (Play.mode == Mode.DEV) {
				Logger.error(res.getError(), "Error executing job");
			}			
		} else {
			eb.setSuccessful(true);
		}		
		logResult(res, eb);
		return res;
	}	
	
	protected abstract T getEvent();

	protected void logResult(GenericJobResult res, T event) {		
		event.update();		
	}
	
	protected void sleep(int seconds) {
		try {
			Thread.sleep(seconds * 1000);
		} catch (Exception ex) {
			//fuck off java
		}
	}
	
}
