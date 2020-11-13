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
package io.seata.rm.datasource.exec;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;

import io.seata.rm.datasource.AbstractConnectionProxy;
import io.seata.rm.datasource.ConnectionContext;
import io.seata.rm.datasource.ConnectionProxy;
import io.seata.rm.datasource.StatementProxy;
import io.seata.rm.datasource.sql.struct.TableRecords;
import io.seata.sqlparser.SQLRecognizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Abstract dml base executor.
 *
 * @param <T> the type parameter
 * @param <S> the type parameter
 * @author sharajava
 */
public abstract class AbstractDMLBaseExecutor<T, S extends Statement> extends BaseTransactionalExecutor<T, S> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDMLBaseExecutor.class);

    /**
     * Instantiates a new Abstract dml base executor.
     *
     * @param statementProxy    the statement proxy
     * @param statementCallback the statement callback
     * @param sqlRecognizer     the sql recognizer
     */
    public AbstractDMLBaseExecutor(StatementProxy<S> statementProxy, StatementCallback<T, S> statementCallback,
                                   SQLRecognizer sqlRecognizer) {
        super(statementProxy, statementCallback, sqlRecognizer);
    }

    @Override
    public T doExecute(Object... args) throws Throwable {
        // 获取连接代理
        AbstractConnectionProxy connectionProxy = statementProxy.getConnectionProxy();
        // 判断是否自动提交，其实下面实现方式一样， 所谓AutoCommittrue就是先把commit标志设置为false
        if (connectionProxy.getAutoCommit()) {
            return executeAutoCommitTrue(args);
        } else {
            return executeAutoCommitFalse(args);
        }
    }

    /**
     * Execute auto commit false t.
     *
     * @param args the args
     * @return the t
     * @throws Exception the exception
     */
    protected T executeAutoCommitFalse(Object[] args) throws Exception {
        // 获取SQL执行前数据
        TableRecords beforeImage = beforeImage();
        // 执行sql
        T result = statementCallback.execute(statementProxy.getTargetStatement(), args);
        // 获取SQL执行后的数据
        TableRecords afterImage = afterImage(beforeImage);
        // undolog
        prepareUndoLog(beforeImage, afterImage);
        return result;
    }

    /**
     * Execute auto commit true t.
     *
     * @param args the args
     * @return the t
     * @throws Throwable the throwable
     */
    protected T executeAutoCommitTrue(Object[] args) throws Throwable {
        ConnectionProxy connectionProxy = statementProxy.getConnectionProxy();
        try {
            // 把autocommit手动设置false
            connectionProxy.setAutoCommit(false);
            // 下面的方法在遇到全局锁的时候，会重试
            return new LockRetryPolicy(connectionProxy).execute(() -> {
                // 这里仅仅是执行sql 生成undolog ，并不会报锁冲突
                T result = executeAutoCommitFalse(args);
                // 在提交前，要拿到全局锁，如果没有，则要重试
                connectionProxy.commit();
                return result;
            });
        } catch (Exception e) {
            // when exception occur in finally,this exception will lost, so just print it here
            LOGGER.error("execute executeAutoCommitTrue error:{}", e.getMessage(), e);
            if (!LockRetryPolicy.isLockRetryPolicyBranchRollbackOnConflict()) {
                connectionProxy.getTargetConnection().rollback();
            }
            throw e;
        } finally {
            connectionProxy.getContext().reset();
            connectionProxy.setAutoCommit(true);
        }
    }

    /**
     * Before image table records.
     *
     * @return the table records
     * @throws SQLException the sql exception
     */
    protected abstract TableRecords beforeImage() throws SQLException;

    /**
     * After image table records.
     *
     * @param beforeImage the before image
     * @return the table records
     * @throws SQLException the sql exception
     */
    protected abstract TableRecords afterImage(TableRecords beforeImage) throws SQLException;

    private static class LockRetryPolicy extends ConnectionProxy.LockRetryPolicy {
        private final ConnectionProxy connection;

        LockRetryPolicy(final ConnectionProxy connection) {
            this.connection = connection;
        }

        @Override
        public <T> T execute(Callable<T> callable) throws Exception {
            if (LOCK_RETRY_POLICY_BRANCH_ROLLBACK_ON_CONFLICT) {
                return doRetryOnLockConflict(callable);
            } else {
                return callable.call();
            }
        }

        @Override
        protected void onException(Exception e) throws Exception {
            ConnectionContext context = connection.getContext();
            //UndoItems can't use the Set collection class to prevent ABA
            context.getUndoItems().clear();
            context.getLockKeysBuffer().clear();
            connection.getTargetConnection().rollback();
        }

        public static boolean isLockRetryPolicyBranchRollbackOnConflict() {
            return LOCK_RETRY_POLICY_BRANCH_ROLLBACK_ON_CONFLICT;
        }
    }
}
