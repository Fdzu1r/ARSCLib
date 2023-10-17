/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.dex.ins;

import com.reandroid.arsc.base.Block;
import com.reandroid.arsc.base.BlockCounter;
import com.reandroid.arsc.container.BlockList;
import com.reandroid.arsc.io.BlockReader;
import com.reandroid.dex.base.Sle128Item;
import com.reandroid.dex.data.DexContainerItem;
import com.reandroid.dex.data.InstructionList;
import com.reandroid.utils.collection.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.Function;

public class TryItem extends DexContainerItem implements Iterable<Label>{
    private final HandlerOffsetArray handlerOffsetArray;

    final Sle128Item handlersCount;
    private final BlockList<CatchTypedHandler> catchTypedHandlerList;
    private CatchAllHandler catchAllHandler;

    public TryItem(HandlerOffsetArray handlerOffsetArray) {
        super(3);

        this.handlerOffsetArray = handlerOffsetArray;
        this.handlersCount = new Sle128Item();
        this.catchTypedHandlerList = new BlockList<>();

        addChild(0, handlersCount);
        addChild(1, catchTypedHandlerList);
    }
    private TryItem() {
        super(0);
        this.handlerOffsetArray = null;

        this.handlersCount = null;
        this.catchTypedHandlerList = null;
    }

    InstructionList getInstructionList(){
        return getTryBlock().getInstructionList();
    }
    TryBlock getTryBlock(){
        return getParent(TryBlock.class);
    }

    TryItem newCopy(){
        return new Copy(this);
    }
    HandlerOffset getHandlerOffset() {
        return getHandlerOffsetArray().get(getIndex());
    }
    HandlerOffsetArray getHandlerOffsetArray(){
        return handlerOffsetArray;
    }
    BlockList<CatchTypedHandler> getCatchTypedHandlerBlockList(){
        return catchTypedHandlerList;
    }
    Iterator<CatchTypedHandler> getCatchTypedHandlers(){
        return catchTypedHandlerList.iterator();
    }
    TryItem getTryItem(){
        return this;
    }

    @Override
    public Iterator<Label> iterator(){
        return new ExpandIterator<>(getExceptionHandlers());
    }
    public Iterator<ExceptionHandler> getExceptionHandlers(){
        Iterator<ExceptionHandler> iterator1 = EmptyIterator.of();
        ExceptionHandler handler = getCatchAllHandler();
        if(handler != null){
            iterator1 = SingleIterator.of(handler);
        }
        return new CombiningIterator<>(getCatchTypedHandlers(), iterator1);
    }
    public int getStartAddress(){
        return getHandlerOffset().getStartAddress();
    }
    public void setStartAddress(int address){
        getHandlerOffset().setStartAddress(address);
    }
    public int getCatchCodeUnit(){
        return getHandlerOffset().getCatchCodeUnit();
    }

    public boolean hasCatchAllHandler(){
        return getCatchAllHandler() != null;
    }
    public CatchAllHandler getCatchAllHandler(){
        return catchAllHandler;
    }
    private CatchAllHandler initCatchAllHandler(){
        CatchAllHandler catchAllHandler = this.getCatchAllHandler();
        if(catchAllHandler == null){
            catchAllHandler = new CatchAllHandler();
            addChild(2, catchAllHandler);
            this.catchAllHandler = catchAllHandler;
        }
        return catchAllHandler;
    }

    @Override
    public void onReadBytes(BlockReader reader) throws IOException {
        int maxPosition = reader.getPosition();

        HandlerOffsetArray handlerOffsetArray = this.getHandlerOffsetArray();
        int position = handlerOffsetArray.getItemsStart()
                + handlerOffsetArray.getOffset(this.getIndex());
        reader.seek(position);
        this.handlersCount.readBytes(reader);
        int count = this.handlersCount.get();
        boolean hasCatchAll = false;
        if(count <= 0){
            count = -count;
            hasCatchAll = true;
        }
        BlockList<CatchTypedHandler> handlerList = this.getCatchTypedHandlerBlockList();
        for(int i = 0; i < count; i++){
            CatchTypedHandler handler = new CatchTypedHandler();
            handlerList.add(handler);
            handler.readBytes(reader);
        }
        if(hasCatchAll){
            initCatchAllHandler().readBytes(reader);
        }
        if(maxPosition > reader.getPosition()){
            // Should never reach here
            reader.seek(maxPosition);
        }
    }
    @Override
    public void onCountUpTo(BlockCounter counter) {
        if(counter.FOUND){
            return;
        }
        Block end = counter.END;
        if(end instanceof TryItem.Copy){
            TryItem tryItem = ((TryItem.Copy) end).getTryItem();
            if(tryItem == this){
                counter.FOUND = true;
                return;
            }
        }
        super.onCountUpTo(counter);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        Iterator<ExceptionHandler> handlers = getExceptionHandlers();
        while (handlers.hasNext()){
            if(builder.length() > 0){
                builder.append("\n");
            }
            builder.append(handlers.next());
        }
        return builder.toString();
    }
    static class Copy extends TryItem{
        private final TryItem tryItem;
        public Copy(TryItem tryItem) {
            super();
            this.tryItem = tryItem;
        }

        @Override
        TryBlock getTryBlock() {
            return tryItem.getTryBlock();
        }

        @Override
        TryItem newCopy() {
            return tryItem.newCopy();
        }
        @Override
        HandlerOffsetArray getHandlerOffsetArray(){
            return tryItem.getHandlerOffsetArray();
        }
        @Override
        Iterator<CatchTypedHandler> getCatchTypedHandlers(){
            return ComputeIterator.of(tryItem.getCatchTypedHandlers(), new Function<CatchTypedHandler, CatchTypedHandler>() {
                @Override
                public CatchTypedHandler apply(CatchTypedHandler catchTypedHandler) {
                    CatchTypedHandler copy = catchTypedHandler.newCopy();
                    copy.setParent(Copy.this);
                    return copy;
                }
            });
        }
        @Override
        TryItem getTryItem(){
            return tryItem.getTryItem();
        }
        @Override
        public CatchAllHandler getCatchAllHandler(){
            CatchAllHandler catchAllHandler = tryItem.getCatchAllHandler();
            if(catchAllHandler != null){
                CatchAllHandler copy = catchAllHandler.newCopy();
                copy.setParent(this);
                return copy;
            }
            return null;
        }

        @Override
        public int countBytes() {
            return 0;
        }
        @Override
        public int onWriteBytes(OutputStream stream) throws IOException {
            return 0;
        }
        @Override
        public byte[] getBytes() {
            return null;
        }
        @Override
        protected void onPreRefresh() {
        }
        @Override
        protected void onRefreshed() {
        }

        @Override
        public void onReadBytes(BlockReader reader) throws IOException {
        }
    }
}
