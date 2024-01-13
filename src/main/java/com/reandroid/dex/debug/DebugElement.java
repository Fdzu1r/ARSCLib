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
package com.reandroid.dex.debug;

import com.reandroid.arsc.io.BlockReader;
import com.reandroid.arsc.item.ByteItem;
import com.reandroid.dex.base.DexException;
import com.reandroid.dex.id.IdItem;
import com.reandroid.dex.ins.ExtraLine;
import com.reandroid.dex.data.FixedDexContainerWithTool;
import com.reandroid.dex.smali.SmaliDirective;
import com.reandroid.dex.smali.SmaliWriter;
import com.reandroid.dex.smali.model.SmaliDebug;
import com.reandroid.utils.collection.EmptyIterator;

import java.io.IOException;
import java.util.Iterator;

public abstract class DebugElement extends FixedDexContainerWithTool implements ExtraLine {
    private final ByteItem elementType;
    private int address;
    private int lineNumber;

    DebugElement(int childesCount, int flag) {
        super(childesCount + 1);

        this.elementType = new ByteItem();
        this.elementType.set((byte) flag);

        addChild(0, elementType);
    }
    DebugElement(int childesCount, DebugElementType<?> elementType) {
        this(childesCount, elementType.getFlag());
    }
    DebugElement(DebugElementType<?> elementType) {
        this(0, elementType.getFlag());
    }

    public void removeSelf(){
        DebugSequence debugSequence = getDebugSequence();
        if(debugSequence != null){
            debugSequence.remove(this);
        }
    }
    public boolean isValid(){
        return getParent() != null;
    }

    int getAddressDiff(){
        return 0;
    }
    void setAddressDiff(int diff){
        if(diff == 0){
            return;
        }
        DebugAdvancePc advancePc = getOrCreateDebugAdvancePc();
        advancePc.setAddressDiff(diff);
    }
    int getLineDiff(){
        return 0;
    }
    void setLineDiff(int diff){
    }

    DebugSequence getDebugSequence(){
        return getParent(DebugSequence.class);
    }

    @Override
    public int getTargetAddress() {
        return address;
    }
    @Override
    public void setTargetAddress(int address) {
        if(address == getTargetAddress()){
            return;
        }
        DebugSequence sequence = getDebugSequence();
        if(sequence == null){
            return;
        }
        DebugElement previous = sequence.get(getIndex() - 1);
        int diff;
        if(previous == null){
            diff = address;
        }else {
            diff = address - previous.getTargetAddress();
            if(diff < 0){
                diff = 0;
            }
        }
        setAddressDiff(diff);
        this.address = address;
        DebugElement next = sequence.get(getIndex() + 1);
        if(next != null){
            next.setTargetAddress(address + next.getAddressDiff());
        }
    }
    private DebugAdvancePc getOrCreateDebugAdvancePc(){
        DebugAdvancePc advancePc = getDebugAdvancePc();
        if(advancePc != null){
            return advancePc;
        }
        DebugSequence debugSequence = getDebugSequence();
        if(debugSequence != null){
            advancePc = debugSequence.createAtPosition(DebugElementType.ADVANCE_PC, getIndex());
        }
        return advancePc;
    }
    private DebugAdvancePc getDebugAdvancePc(){
        DebugSequence debugSequence = getDebugSequence();
        if(debugSequence != null){
            DebugElement element = debugSequence.get(getIndex() - 1);
            if(element instanceof DebugAdvanceLine){
                element = debugSequence.get(element.getIndex() - 1);
            }
            if(element instanceof DebugAdvancePc){
                return (DebugAdvancePc) element;
            }
        }
        return null;
    }
    int getLineNumber(){
        return lineNumber;
    }
    void setLineNumber(int lineNumber){
        this.lineNumber = lineNumber;
    }

    int getFlag(){
        int flag = elementType.unsignedInt();
        if(flag > 0x0A){
            flag = 0x0A;
        }
        return flag;
    }
    int getFlagOffset(){
        int offset = elementType.unsignedInt();
        if(offset < 0x0A){
            return 0;
        }
        return offset - 0x0A;
    }
    void setFlagOffset(int offset){
        int flag = getFlag();
        if(flag < 0x0A){
            if(offset == 0){
                return;
            }
            throw new IllegalArgumentException("Can not set offset for: " + getElementType());
        }
        if(offset < 0 || offset > 0xF5){
            throw new DexException("Value out of range should be [0 - 245]: " + offset + ", prev = " + getFlagOffset());
        }
        int value = flag + offset;
        elementType.set((byte) value);
    }
    public abstract DebugElementType<?> getElementType();
    public SmaliDirective getSmaliDirective(){
        return getElementType().getSmaliDirective();
    }
    void cacheValues(DebugSequence debugSequence, DebugElement previous){
        int line;
        int address;
        if(previous == null){
            address = 0;
            line = debugSequence.getLineStart();
        }else {
            address = previous.getTargetAddress();
            line = previous.getLineNumber();
        }
        address += getAddressDiff();
        line += getLineDiff();
        this.address = address;
        this.lineNumber = line;
    }
    void updateValues(DebugSequence debugSequence, DebugElement previous){
        if(previous == this){
            return;
        }
        if(previous != null && previous.getParent() == null){
            return;
        }
        int line;
        int address;
        if(previous == null){
            address = 0;
            line = debugSequence.getLineStart();
        }else {
            address = previous.getTargetAddress();
            line = previous.getLineNumber();
        }
        int addressDiff = getTargetAddress() - address;
        int lineDiff = getLineNumber() - line;
        setAddressDiff(addressDiff);
        setLineDiff(lineDiff);
    }
    void onPreRemove(DebugSequence debugSequence){
        transferLineOffset(debugSequence);
    }
    private void transferLineOffset(DebugSequence debugSequence){
        int diff = getLineDiff();
        if(diff == 0){
            return;
        }
        DebugElement prev = debugSequence.get(getIndex() - 1);
        if(prev == null){
            debugSequence.setLineStart(debugSequence.getLineStart() + diff);
            return;
        }
        int available = 245 - prev.getLineDiff();
        if(available > 0){
            if(diff > available){
                prev.setLineDiff(prev.getLineDiff() + available);
                diff = diff - available;
            }else {
                prev.setLineDiff(prev.getLineDiff() + diff);
                diff = 0;
            }
        }
        if(diff == 0){
            return;
        }
        DebugElement next = debugSequence.get(getIndex() + 1);
        if(next == null){
            return;
        }
        available = 245 - next.getLineDiff();
        if(available > 0){
            if(diff > available){
                next.setLineDiff(prev.getLineDiff() + available);
            }else {
                next.setLineDiff(prev.getLineDiff() + diff);
            }
        }

    }

    @Override
    public void onReadBytes(BlockReader reader) throws IOException {
        super.nonCheckRead(reader);
    }

    @Override
    public void appendExtra(SmaliWriter writer) throws IOException {
        getSmaliDirective().append(writer);
    }
    @Override
    public boolean isEqualExtraLine(Object obj) {
        return obj == this;
    }
    @Override
    public int getSortOrder() {
        return ExtraLine.ORDER_DEBUG_LINE;
    }

    public Iterator<IdItem> usedIds(){
        return EmptyIterator.of();
    }
    public void merge(DebugElement element){
        this.elementType.set(element.elementType.get());
    }
    public void fromSmali(SmaliDebug smaliDebug) throws IOException{
        setTargetAddress(smaliDebug.getAddress());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DebugElement element = (DebugElement) obj;
        return elementType.get() == element.elementType.get();
    }
    @Override
    public int hashCode() {
        return elementType.get();
    }

    @Override
    public String toString() {
        return "Type = " + getElementType();
    }
}
