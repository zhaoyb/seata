/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.tm.api;

import io.seata.core.exception.TransactionException;
import io.seata.core.model.GlobalStatus;

/**
 * Global transaction.
 *
 * TM 全局事物
 *
 * @author sharajava
 */
public interface GlobalTransaction {

    /**
     * 用默认的超时时间和名称 开启一个新事务
     *
     * Begin a new global transaction with default timeout and name.
     *
     *
     * @throws TransactionException Any exception that fails this will be wrapped with TransactionException and thrown
     * out.
     */
    void begin() throws TransactionException;

    /**
     *
     * 用给定的超时时间 开启一个新事务
     *
     * Begin a new global transaction with given timeout and default name.
     *
     *
     * @param timeout Global transaction timeout in MILLISECONDS
     * @throws TransactionException Any exception that fails this will be wrapped with TransactionException and thrown
     * out.
     */
    void begin(int timeout) throws TransactionException;

    /**
     * Begin a new global transaction with given timeout and given name.
     *
     * @param timeout Given timeout in MILLISECONDS.
     * @param name    Given name.
     * @throws TransactionException Any exception that fails this will be wrapped with TransactionException and thrown
     * out.
     */
    void begin(int timeout, String name) throws TransactionException;

    /**
     *
     * 提交一个全局事务
     *
     * Commit the global transaction.
     *
     * @throws TransactionException Any exception that fails this will be wrapped with TransactionException and thrown
     * out.
     */
    void commit() throws TransactionException;

    /**
     * 回滚一个全局事务
     *
     * Rollback the global transaction.
     *
     * @throws TransactionException Any exception that fails this will be wrapped with TransactionException and thrown
     * out.
     */
    void rollback() throws TransactionException;

    /**
     *
     * 获取事务状态
     *
     * Ask TC for current status of the corresponding global transaction.
     *
     * @return Status of the corresponding global transaction.
     * @throws TransactionException Any exception that fails this will be wrapped with TransactionException and thrown
     * out.
     * @see GlobalStatus
     */
    GlobalStatus getStatus() throws TransactionException;

    /**
     * Get XID.
     *
     * @return XID. xid
     */
    String getXid();

    /**
     * report the global transaction status.
     *
     * @param globalStatus global status.
     *
     * @throws TransactionException Any exception that fails this will be wrapped with TransactionException and thrown
     * out.
     */
    void globalReport(GlobalStatus globalStatus) throws TransactionException;

}
