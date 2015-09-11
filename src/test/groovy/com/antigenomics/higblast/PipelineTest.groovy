/*
 * Copyright 2013-2015 Mikhail Shugay (mikhail.shugay@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.antigenomics.higblast

import com.antigenomics.higblast.blast.BlastInstanceFactory
import com.antigenomics.higblast.genomic.SegmentDatabase
import com.antigenomics.higblast.io.*
import com.antigenomics.higblast.mapping.ReadMapping
import com.antigenomics.higblast.mapping.ReadMappingFilter
import org.junit.AfterClass
import org.junit.Test

import java.util.concurrent.atomic.AtomicInteger

class PipelineTest {
    @Test
    void sampleTest() {
        def reader = new FastqReader("sample.fastq.gz", true)
        def factory = new BlastInstanceFactory("data/", "human", ["IGH"], true, false)
        def filter = new ReadMappingFilter()

        def pipeline = new Pipeline(reader, factory,
                new InputPortMerge(new ReadMappingOutput(), new ClonotypeOutput()),
                filter)

        pipeline.run()

        assert pipeline.inputCount == 1000
        assert pipeline.readMappingFilter.mappedRatio >= 0.95
        assert pipeline.readMappingFilter.noCdr3Ratio <= 0.1
        assert pipeline.readMappingFilter.incompleteRatio <= 0.1
        assert pipeline.readMappingFilter.nonCanonicalRatio <= 0.1

        println filter.toProgressString()
    }

    @Test
    void fastaTest() {
        def reader = new FastaReader("sample.fasta.gz", true)
        def factory = new BlastInstanceFactory("data/", "human", ["IGH"], true, false)

        def pipeline = new Pipeline(reader, factory,
                DummyInputPort.INSTANCE,
                ReadMappingFilter.createDummy())

        pipeline.run()

        assert pipeline.readMappingFilter.total == 100
        assert pipeline.readMappingFilter.goodRatio == 1.0
    }

    @Test
    void dAssignmentTest() {
        def reader = new FastqReader("ambiguous_d.fastq.gz", true)
        def factory = new BlastInstanceFactory("data/", "human", ["IGH"], true, false)

        def badDCount = new AtomicInteger(),
                wrongMappingCount = new AtomicInteger()

        def pipeline = new Pipeline(reader, factory,
                new InputPort<ReadMapping>() {
                    @Override
                    void put(ReadMapping obj) {
                        if (obj.mapped && obj.cdr3nt == "TGTGCGAGCGATCGGAACGGTATGGACGTCTGG") {
                            if (!obj.mapping.dSegment.name == "IGHD1-1*01")
                                badDCount.incrementAndGet()
                        } else {
                            wrongMappingCount.incrementAndGet()
                        }
                    }

                    @Override
                    void close() {

                    }
                },
                ReadMappingFilter.createDummy())

        pipeline.run()
        
        assert badDCount.get() == 0
        assert wrongMappingCount.get() == 0
    }

    @Test
    void badDataTest() {
        def reader = new FastqReader("bad_sample.fastq.gz", true)
        def factory = new BlastInstanceFactory("data/", "human", ["IGH"], true, false)

        def filter = new ReadMappingFilter((byte) 20, true, true, true, true)

        def pipeline = new Pipeline(reader, factory, DummyInputPort.INSTANCE,
                filter)

        pipeline.run()

        assert filter.passed == filter.good
        assert pipeline.readMappingFilter.mappedRatio >= 0.05

        println filter.toProgressString()
    }

    @AfterClass
    static void tearDown() {
        SegmentDatabase.clearTemporaryFiles()
    }
}
