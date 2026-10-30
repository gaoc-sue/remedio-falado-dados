package br.com.remediofalado.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Tarefa do Android que roda a conexão com a família mesmo com o app fechado. */
public class EloJob extends JobService {

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try { Elo.executar(getApplicationContext()); } catch (Throwable t) { }
                try { Elo.agendarAtraso(getApplicationContext()); } catch (Throwable t) { }
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
