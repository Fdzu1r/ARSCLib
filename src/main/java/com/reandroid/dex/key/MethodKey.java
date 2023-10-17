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
package com.reandroid.dex.key;

import com.reandroid.dex.common.DexUtils;
import com.reandroid.dex.id.MethodId;
import com.reandroid.utils.CompareUtil;
import com.reandroid.utils.StringsUtil;

public class MethodKey implements Key{

    private final String defining;
    private final String name;
    private final String[] parameters;
    private final String returnType;

    private int mParamsHash;

    public MethodKey(String defining, String name, String[] parameters, String returnType){
        this.defining = defining;
        this.name = name;
        this.parameters = parameters;
        this.returnType = returnType;
    }


    public MethodKey changeDefining(TypeKey typeKey){
        return changeDefining(typeKey.getType());
    }
    public MethodKey changeDefining(String defining){
        if(defining.equals(getDefining())){
            return this;
        }
        return new MethodKey(defining, getName(), getParameters(), getReturnType());
    }
    public MethodKey changeName(String name){
        if(name.equals(getName())){
            return this;
        }
        return new MethodKey(getDefining(), name, getParameters(), getReturnType());
    }
    public MethodKey changeParameters(String[] parameters){
        if(parameters == getParameters()){
            return this;
        }
        return new MethodKey(getDefining(), getName(), parameters, getReturnType());
    }
    public MethodKey removeParameter(int index){
        ProtoKey protoKey = getProtoKey();
        protoKey = protoKey.removeParameter(index);
        return new MethodKey(getDefining(), getName(), protoKey.getParameters(), getReturnType());
    }
    public TypeKey getDefiningKey() {
        return new TypeKey(getDefining());
    }
    public StringKey getNameKey() {
        return new StringKey(getName());
    }
    public ProtoKey getProtoKey() {
        return new ProtoKey(getParameters(), getReturnType());
    }
    public TypeKey getReturnTypeKey() {
        return new TypeKey(getReturnType());
    }

    public String getDefining() {
        return defining;
    }
    public String getName() {
        return name;
    }
    public String[] getParameters() {
        return parameters;
    }
    public int getParametersCount() {
        String[] parameters = getParameters();
        if(parameters != null){
            return parameters.length;
        }
        return 0;
    }
    public String getParameter(int i){
        return getParameters()[i];
    }
    public TypeKey getParameterType(int i){
        return TypeKey.create(getParameter(i));
    }
    public String getReturnType() {
        return returnType;
    }

    @Override
    public int compareTo(Object obj) {
        if(obj == null){
            return -1;
        }
        MethodKey key = (MethodKey) obj;
        int i = CompareUtil.compare(getDefining(), key.getDefining());
        if(i != 0) {
            return i;
        }
        i = CompareUtil.compare(getName(), key.getName());
        if(i != 0) {
            return i;
        }
        i = CompareUtil.compare(getParameters(), key.getParameters());
        if(i != 0) {
            return i;
        }
        return CompareUtil.compare(getReturnType(), key.getReturnType());
    }
    public int compareTo(Object obj, boolean checkDefining) {
        if(obj == null){
            return -1;
        }
        MethodKey key = (MethodKey) obj;
        int i;
        if(checkDefining){
            i = CompareUtil.compare(getDefining(), key.getDefining());
            if(i != 0) {
                return i;
            }
        }
        i = CompareUtil.compare(getName(), key.getName());
        if(i != 0) {
            return i;
        }
        i = CompareUtil.compare(getParameters(), key.getParameters());
        if(i != 0) {
            return i;
        }
        return CompareUtil.compare(getReturnType(), key.getReturnType());
    }

    @Override
    public boolean equals(Object obj) {
        return equals(obj, true, false);
    }
    public boolean equals(Object obj, boolean checkDefining, boolean checkType) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MethodKey)) {
            return false;
        }
        MethodKey methodKey = (MethodKey) obj;
        if(getNameParamsHashCode() != methodKey.getNameParamsHashCode()){
            return false;
        }
        if(!KeyUtil.matches(getName(), methodKey.getName())){
            return false;
        }
        int i = CompareUtil.compare(getParameters(), methodKey.getParameters());
        if(i != 0) {
            return false;
        }
        if(checkDefining){
            if(!KeyUtil.matches(getDefining(), methodKey.getDefining())){
                return false;
            }
        }
        if(checkType){
            return KeyUtil.matches(getReturnType(), methodKey.getReturnType());
        }
        return true;
    }


    @Override
    public int hashCode() {
        int hash = 1;
        String defining = getDefining();
        if(defining != null){
            hash += defining.hashCode();
        }
        hash = hash * 31 + getNameParamsHashCode();
        return hash;
    }
    private int getNameParamsHashCode() {
        int hash = mParamsHash;
        if(hash != 0){
            return hash;
        }
        hash = 31 + getName().hashCode();
        String[] parameters = getParameters();
        if(parameters != null){
            for(String param : parameters){
                hash = hash * 31 + param.hashCode();
            }
        }
        mParamsHash = hash;
        return hash;
    }
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        String defining = getDefining();
        if(defining != null){
            builder.append(defining);
            builder.append("->");
        }
        builder.append(getName());
        builder.append('(');
        String[] parameters = getParameters();
        if(parameters != null){
            for (String parameter : parameters) {
                builder.append(parameter);
            }
        }
        builder.append(')');
        String type = getReturnType();
        if(type != null){
            builder.append(type);
        }
        return builder.toString();
    }

    public static MethodKey parse(String text) {
        if(text == null){
            return null;
        }
        text = text.trim();
        if(text.length() < 6 || (text.charAt(0) != 'L' && text.charAt(0) != '[')){
            return null;
        }
        int i = text.indexOf(";->");
        if(i < 0){
            return null;
        }
        String defining = text.substring(0, i + 1);
        text = text.substring(i + 3);
        i = text.indexOf('(');
        if(i < 0){
            return null;
        }
        String name = text.substring(0, i);
        text = text.substring(i + 1);
        i = text.indexOf(')');
        if(i < 0){
            return null;
        }
        String[] parameters = DexUtils.splitParameters(text.substring(0, i));
        text = text.substring(i + 1);
        String returnType = null;
        if(!StringsUtil.isEmpty(text)){
            returnType = text;
        }
        return new MethodKey(defining, name, parameters, returnType);
    }

    public static MethodKey create(MethodId methodId){
        String defining = methodId.getClassName();
        if(defining == null){
            return null;
        }
        String name = methodId.getName();
        if(name == null){
            return null;
        }
        return new MethodKey(defining, name, methodId.getParameterNames(), methodId.getReturnType());
    }
}
