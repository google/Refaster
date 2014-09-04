/*
 * Copyright 2011 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.apply;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Applier of diffs to Java source code
 * 
 * @author alexeagle@google.com (Alex Eagle)
 * @author sjnickerson@google.com (Simon Nickerson)
 */
public class DiffApplier {
  private static final Logger logger = Logger.getLogger(DiffApplier.class.getName());
  private final int diffParallelism;
  private final FileSource source;
  private final FileDestination destination;

  public DiffApplier(int diffParallelism, FileSource source, FileDestination destination) {
    Preconditions.checkNotNull(source);
    Preconditions.checkNotNull(destination);
    this.diffParallelism = diffParallelism;
    this.source = source;
    this.destination = destination;
  }

  public void apply(Iterable<? extends Diff> diffs, boolean keepGoing) throws IOException {
    ExecutorService executor = Executors.newFixedThreadPool(diffParallelism);
    CompletionService<Diff> service = new ExecutorCompletionService<>(executor);
    Set<String> diffFilesNotApplied = Collections.newSetFromMap(
        new ConcurrentHashMap<String, Boolean>());
    Iterator<? extends Diff> diffItr = diffs.iterator();
    final Stopwatch stopwatch = Stopwatch.createStarted();
    int pending;
    for (pending = 0; pending < diffParallelism && diffItr.hasNext(); pending++) {
      Diff diff = diffItr.next();
      service.submit(new DiffRunner(diffFilesNotApplied, diff));
    }
    int completed = 0;
    while (pending > 0) {
      try {
        service.take().get();
        completed++;
      } catch (ExecutionException e) {
        if (!keepGoing) {
          throw new IOException(e.getCause());
        } else {
          // Report the error, just so we know what happened, even if we want to keep building the
          // change.
          e.printStackTrace();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
        return;
      }
      pending--;
      if (completed % 100 == 0) {
        logger.log(INFO, String.format("%d files completed in %s: %.2f/second",
            completed, stopwatch, completed * 1000.0 / stopwatch.elapsed(TimeUnit.MILLISECONDS)));
      }
      if (diffItr.hasNext()) {
        service.submit(new DiffRunner(diffFilesNotApplied, diffItr.next()));
        pending++;
      }
    }
    logger.log(INFO, String.format("%d files completed in %s: %.2f/second",
        completed, stopwatch, completed * 1000.0 / stopwatch.elapsed(TimeUnit.MILLISECONDS)));
    executor.shutdown();
    destination.flush();
    if (!diffFilesNotApplied.isEmpty()) {
      logger.log(WARNING, String.format("diffs to %d files couldn't be applied: %s",
          diffFilesNotApplied.size(), diffFilesNotApplied));
    }
  }

  private class DiffRunner implements Callable<Diff> {
    private final Set<String> diffFilesNotApplied;
    private final Diff diff;

    DiffRunner(Set<String> diffFilesNotApplied, Diff diff) {
      this.diffFilesNotApplied = diffFilesNotApplied;
      this.diff = diff;
    }

    @Override public Diff call()
        throws IOException, FileNotFoundException, DiffNotApplicableException {
      try {
        SourceFile file = source.readFile(diff.getRelevantFileName());
        diff.applyDifferences(file);
        destination.writeFile(file);
      } catch (DiffNotApplicableException e) {
        handleFailedDiffApplication(
            e, "Could not apply diffs to file %s", diff.getRelevantFileName());
      } catch (FileNotFoundException e) {
        handleFailedDiffApplication(
            e, "File %s not found", diff.getRelevantFileName());
      } catch (IOException e) {
        handleFailedDiffApplication(
            e, "IOException for file %s", diff.getRelevantFileName());
      }
      return diff;
    }

    /**
     * Handles a failure to apply a stated {@link Diff}
     *
     * @param <X> the type of exception that caused the failure
     * @param throwable the exception that caused the diff application failure
     * @param errorFormatString a format string for a warning message
     * @param errorFormatArgs arguments for the warning message
     * @throws X if keepGoing is false
     */
    private <X extends Throwable> void handleFailedDiffApplication(
        X throwable, String errorFormatString, Object... errorFormatArgs) throws X {
      logger.log(WARNING, String.format(errorFormatString + ", continuing anyway", errorFormatArgs),
          throwable);
      diffFilesNotApplied.add(diff.getRelevantFileName());
      throw throwable;
    }
  }

}
