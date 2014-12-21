//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.server;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.EOFException;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ReadListener;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.ConcurrentArrayQueue;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HttpInputTest
{
    Queue<String> _history = new ConcurrentArrayQueue<String>()
            {
                @Override
                public boolean add(String s)
                {
                    //System.err.println("history: "+s);
                    return super.add(s);
                }
            };
    Queue<String> _content = new ConcurrentArrayQueue<>();
    HttpInput _in;
    
    ReadListener _listener = new ReadListener()
    {
        @Override
        public void onError(Throwable t)
        {
            _history.add("onError:"+t);
        }
        
        @Override
        public void onDataAvailable() throws IOException
        {
            _history.add("onDataAvailable");
        }
        
        @Override
        public void onAllDataRead() throws IOException
        {
            _history.add("onAllDataRead");
        }
    };
    
    
    @Before
    public void before()
    {
        _in=new HttpInput()
        {
            @Override
            protected void onReadPossible()
            {     
                _history.add("onReadPossible");        
            }

            @Override
            protected void produceContent() throws IOException
            {
                _history.add("produceContent "+_content.size()); 
                
                for (String s=_content.poll();s!=null;s=_content.poll())
                    _in.content(new Content(BufferUtil.toBuffer(s)));
            }

            @Override
            protected void blockForContent() throws IOException
            {
                _history.add("blockForContent"); 
                super.blockForContent();
            }

            @Override
            protected void unready()
            {
                _history.add("unready"); 
            }  
        };
    }
    
    @Test
    public void testEmpty() throws Exception
    {
        assertThat(_in.available(),equalTo(0));
        assertThat(_in.isFinished(),equalTo(false));
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),nullValue());
    }

    @Test
    public void testRead() throws Exception
    {
        _in.content(new HttpInput.Content(BufferUtil.toBuffer("AB")));
        _in.content(new HttpInput.Content(BufferUtil.toBuffer("CD")));
        _content.offer("EF");
        _content.offer("GH");
        assertThat(_in.available(),equalTo(2));
        assertThat(_in.isFinished(),equalTo(false));

        assertThat(_in.getContentConsumed(),equalTo(0L));
        assertThat(_in.read(),equalTo((int)'A'));
        assertThat(_in.getContentConsumed(),equalTo(1L));
        assertThat(_in.read(),equalTo((int)'B'));
        assertThat(_in.read(),equalTo((int)'C'));
        assertThat(_in.read(),equalTo((int)'D'));
        assertThat(_in.read(),equalTo((int)'E'));
        assertThat(_in.read(),equalTo((int)'F'));
        assertThat(_in.read(),equalTo((int)'G'));
        assertThat(_in.read(),equalTo((int)'H'));
        assertThat(_in.getContentConsumed(),equalTo(8L));
        
        assertThat(_history.poll(),equalTo("produceContent 2"));
        assertThat(_history.poll(),nullValue());
    }

    @Test
    public void testBlockingRead() throws Exception
    {
        new Thread()
        {
            public void run() 
            {
                try
                {
                    Thread.sleep(500);
                    _in.content(new HttpInput.Content(BufferUtil.toBuffer("AB")));
                }
                catch(Throwable th)
                {
                    th.printStackTrace();
                }
            }
        }.start();
        
        assertThat(_in.read(),equalTo((int)'A'));
        
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),equalTo("blockForContent"));
        assertThat(_history.poll(),nullValue());
        
        assertThat(_in.read(),equalTo((int)'B'));
    }

    @Test
    public void testReadEOF() throws Exception
    {
        _in.content(new HttpInput.Content(BufferUtil.toBuffer("AB")));
        _in.content(new HttpInput.Content(BufferUtil.toBuffer("CD")));
        _in.eof();

        assertThat(_in.isFinished(),equalTo(false));
        assertThat(_in.available(),equalTo(2));
        assertThat(_in.isFinished(),equalTo(false));

        assertThat(_in.read(),equalTo((int)'A'));
        assertThat(_in.read(),equalTo((int)'B'));
        assertThat(_in.read(),equalTo((int)'C'));
        assertThat(_in.isFinished(),equalTo(false));
        assertThat(_in.read(),equalTo((int)'D'));
        assertThat(_in.isFinished(),equalTo(false));
        assertThat(_in.read(),equalTo(-1));
        assertThat(_in.isFinished(),equalTo(true));
        
        assertThat(_history.poll(),nullValue());
    }

    @Test
    public void testReadEarlyEOF() throws Exception
    {
        _in.content(new HttpInput.Content(BufferUtil.toBuffer("AB")));
        _in.content(new HttpInput.Content(BufferUtil.toBuffer("CD")));
        _in.earlyEOF();

        assertThat(_in.isFinished(),equalTo(false));
        assertThat(_in.available(),equalTo(2));
        assertThat(_in.isFinished(),equalTo(false));

        assertThat(_in.read(),equalTo((int)'A'));
        assertThat(_in.read(),equalTo((int)'B'));
        assertThat(_in.read(),equalTo((int)'C'));
        assertThat(_in.isFinished(),equalTo(false));
        assertThat(_in.read(),equalTo((int)'D'));
        assertThat(_in.isFinished(),equalTo(false));
        
        try
        {
            _in.read();
            fail();
        }
        catch(EOFException eof)
        {
            assertThat(_in.isFinished(),equalTo(true));
        }
        
        assertThat(_history.poll(),nullValue());
    }
    
    @Test
    public void testBlockingEOF() throws Exception
    {
        new Thread()
        {
            public void run() 
            {
                try
                {
                    Thread.sleep(500);
                    _in.eof();
                }
                catch(Throwable th)
                {
                    th.printStackTrace();
                }
            }
        }.start();

        assertThat(_in.isFinished(),equalTo(false));
        assertThat(_in.read(),equalTo(-1));
        assertThat(_in.isFinished(),equalTo(true));
        
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),equalTo("blockForContent"));
        assertThat(_history.poll(),nullValue());   
    }
    
    @Test
    public void testAsyncEmpty() throws Exception
    {
        _in.setReadListener(_listener);
        assertThat(_in.available(),equalTo(0));
        assertThat(_in.isFinished(),equalTo(false));
        assertThat(_in.read(new byte[]{0}),equalTo(0));
        assertThat(_history.poll(),equalTo("onReadPossible"));
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),nullValue());
        _in.run();
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),equalTo("unready"));
        assertThat(_history.poll(),nullValue());
        assertThat(_in.isReady(),equalTo(false));
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),nullValue());
    }
    

    @Test
    public void testAsyncRead() throws Exception
    {
        _in.setReadListener(_listener);
        _in.run();
        assertThat(_history.poll(),equalTo("onReadPossible"));
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),equalTo("unready"));
        assertThat(_history.poll(),nullValue());
        
        _in.content(new HttpInput.Content(BufferUtil.toBuffer("AB")));
        assertThat(_history.poll(),equalTo("onReadPossible"));

        assertThat(_in.isReady(),equalTo(true));
        assertThat(_in.read(),equalTo((int)'A'));
        assertThat(_in.isReady(),equalTo(true));
        assertThat(_in.read(),equalTo((int)'B'));
        assertThat(_in.isReady(),equalTo(false));
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),nullValue());
    }
    
    @Test
    public void testAsyncEOF() throws Exception
    {
        _in.setReadListener(_listener);
        _in.run();
        assertThat(_history.poll(),equalTo("onReadPossible"));
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),equalTo("unready"));
        assertThat(_history.poll(),nullValue());
        
        _in.eof();
        _in.run();
        assertThat(_in.isFinished(),equalTo(true));
        assertThat(_history.poll(),equalTo("onReadPossible"));
        assertThat(_history.poll(),equalTo("onAllDataRead"));
        assertThat(_history.poll(),nullValue());

        assertThat(_in.isReady(),equalTo(true));
        assertThat(_in.read(),equalTo(-1));
        assertThat(_in.isFinished(),equalTo(true));

        assertThat(_history.poll(),nullValue());
    }
    
    @Test
    public void testAsyncError() throws Exception
    {
        _in.setReadListener(_listener);
        _in.run();
        assertThat(_history.poll(),equalTo("onReadPossible"));
        assertThat(_history.poll(),equalTo("produceContent 0"));
        assertThat(_history.poll(),equalTo("unready"));
        assertThat(_history.poll(),nullValue());
        
        _in.failed(new TimeoutException());
        _in.run();
        assertThat(_in.isFinished(),equalTo(true));
        assertThat(_history.poll(),equalTo("onError:java.util.concurrent.TimeoutException"));
        assertThat(_history.poll(),nullValue());

        assertThat(_in.isReady(),equalTo(true));
        
        try
        {
            _in.read();
            fail();
        }
        catch(IOException e)
        {
            assertThat(e.getCause(),Matchers.instanceOf(TimeoutException.class));
            assertThat(_in.isFinished(),equalTo(true));   
        }

        assertThat(_history.poll(),nullValue());
    }
    

    @Test
    public void testRecycle() throws Exception
    {
        testAsyncRead();
        _in.recycle();
        testAsyncRead();
        _in.recycle();
        testReadEOF();
    }
}