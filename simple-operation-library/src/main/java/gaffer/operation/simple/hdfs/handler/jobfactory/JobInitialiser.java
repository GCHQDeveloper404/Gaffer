/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gaffer.operation.simple.hdfs.handler.jobfactory;

import gaffer.operation.simple.hdfs.AddElementsFromHdfs;
import gaffer.store.Store;
import org.apache.hadoop.mapreduce.Job;

import java.io.IOException;

/**
 * A <code>JobInitialiser</code> initialises a job.
 *
 * @see gaffer.operation.simple.hdfs.handler.jobfactory.AvroJobInitialiser
 * @see gaffer.operation.simple.hdfs.handler.jobfactory.TextJobInitialiser
 */
public interface JobInitialiser {
    /**
     * Initialises a job. This will probably involve setting up the job configuration.
     *
     * @param job       the {@link org.apache.hadoop.mapreduce.Job} to be initialised
     * @param operation the {@link gaffer.operation.simple.hdfs.AddElementsFromHdfs} containing configuration.
     * @param store     the {@link gaffer.store.Store} that will handle the {@link gaffer.operation.Operation}
     * @throws IOException
     */
    void initialiseJob(final Job job, final AddElementsFromHdfs operation, final Store store) throws IOException;
}