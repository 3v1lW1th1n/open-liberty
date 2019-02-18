/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The initial set of unit test material was heavily derived from
 * tests at https://github.com/eclipse/microprofile-reactive
 * by James Roper.
 ******************************************************************************/

package com.ibm.ws.microprofile.reactive.streams.test;

import java.util.concurrent.ExecutionException;

import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.Test;

import io.reactivex.Flowable;
import io.reactivex.subscribers.TestSubscriber;

public class RsoElementsTest extends WASReactiveUT {

    @Test
    public void testReactiveStreamsDotOrgSubscriber() throws InterruptedException, ExecutionException {

        PublisherBuilder<Integer> data = ReactiveStreams.of(1, 2, 3, 4, 5);
        TestSubscriber<Integer> rxSub = new TestSubscriber<Integer>();
        data.to(rxSub).run(getEngine());
        rxSub.await()
                .assertComplete()
                .assertNoErrors()
                .assertResult(1, 2, 3, 4, 5);

    }

    @Test
    public void testReactiveStreamsDotOrgPublisher() throws InterruptedException, ExecutionException {

        Flowable<String> flowable = Flowable.fromArray("one", "two", "three");
        PublisherBuilder<String> data = ReactiveStreams.fromPublisher(flowable);

        TestSubscriber<String> rxSub = new TestSubscriber<String>();
        data.to(rxSub).run(getEngine());
        rxSub.await()
                .assertComplete()
                .assertNoErrors()
                .assertResult("one", "two", "three");

    }

    @Test
    public void testDropping() throws InterruptedException, ExecutionException {

        PublisherBuilder<Integer> data = ReactiveStreams.of(1, 2, 3, 4, 5);
        ProcessorBuilder<Integer, Integer> filter = ReactiveStreams.<Integer>builder().dropWhile(t -> t < 3);

        TestSubscriber<Integer> rxSub = new TestSubscriber<Integer>();
        data.via(filter).to(rxSub).run(getEngine());

        TestSubscriber<Integer> o = rxSub.await();
        o.assertComplete();
        o.assertNoErrors();
        o.assertResult(3, 4, 5);

    }

}
