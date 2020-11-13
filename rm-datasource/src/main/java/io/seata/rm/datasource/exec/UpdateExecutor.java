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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import io.seata.common.util.IOUtil;
import io.seata.rm.datasource.StatementProxy;

import io.seata.sqlparser.SQLRecognizer;
import io.seata.sqlparser.SQLUpdateRecognizer;
import io.seata.rm.datasource.sql.struct.Field;
import io.seata.rm.datasource.sql.struct.TableMeta;
import io.seata.rm.datasource.sql.struct.TableRecords;
import org.apache.commons.lang.StringUtils;

/**
 * The type Update executor.
 *
 * @author sharajava
 *
 * @param <T> the type parameter
 * @param <S> the type parameter
 */
public class UpdateExecutor<T, S extends Statement> extends AbstractDMLBaseExecutor<T, S> {

    /**
     * Instantiates a new Update executor.
     *
     * @param statementProxy    the statement proxy
     * @param statementCallback the statement callback
     * @param sqlRecognizer     the sql recognizer
     */
    public UpdateExecutor(StatementProxy<S> statementProxy, StatementCallback<T,S> statementCallback,
                          SQLRecognizer sqlRecognizer) {
        super(statementProxy, statementCallback, sqlRecognizer);
    }

    /**
     * 获取更新前 数据
     *
     * @return
     * @throws SQLException
     */
    @Override
    protected TableRecords beforeImage() throws SQLException {

        ArrayList<List<Object>> paramAppenderList = new ArrayList<>();
        // 获取表结构
        TableMeta tmeta = getTableMeta();
        // 将update 语句，改为select xx from table where yy  for update
        String selectSQL = buildBeforeImageSQL(tmeta, paramAppenderList);
        // 查找记录
        return buildTableRecords(tmeta, selectSQL, paramAppenderList);
    }

    private String buildBeforeImageSQL(TableMeta tableMeta, ArrayList<List<Object>> paramAppenderList) {
        SQLUpdateRecognizer recognizer = (SQLUpdateRecognizer)sqlRecognizer;
        // 获取要更新的列
        List<String> updateColumns = recognizer.getUpdateColumns();
        StringBuilder prefix = new StringBuilder("SELECT ");
        // 判断列是否是主键
        if (!containsPK(updateColumns)) {
            prefix.append(getColumnNameInSQL(tableMeta.getEscapePkName(getDbType()))).append(", ");
        }
        StringBuilder suffix = new StringBuilder(" FROM ").append(getFromTableInSQL());
        String whereCondition = buildWhereCondition(recognizer, paramAppenderList);
        if (StringUtils.isNotBlank(whereCondition)) {
            suffix.append(" WHERE ").append(whereCondition);
        }
        suffix.append(" FOR UPDATE");
        StringJoiner selectSQLJoin = new StringJoiner(", ", prefix.toString(), suffix.toString());
        for (String updateColumn : updateColumns) {
            selectSQLJoin.add(updateColumn);
        }
        return selectSQLJoin.toString();
    }

    /**
     * 获取跟新后数据
     *
     * @param beforeImage the before image
     * @return
     * @throws SQLException
     */
    @Override
    protected TableRecords afterImage(TableRecords beforeImage) throws SQLException {
        TableMeta tmeta = getTableMeta();
        if (beforeImage == null || beforeImage.size() == 0) {
            return TableRecords.empty(getTableMeta());
        }
        String selectSQL = buildAfterImageSQL(tmeta, beforeImage);
        ResultSet rs = null;
        try (PreparedStatement pst = statementProxy.getConnection().prepareStatement(selectSQL)) {
            List<Field> pkRows = beforeImage.pkRows();
            for (int i = 1; i <= pkRows.size(); i++) {
                Field pkField = pkRows.get(i - 1);
                pst.setObject(i, pkField.getValue(), pkField.getType());
            }
            rs = pst.executeQuery();
            return TableRecords.buildRecords(tmeta, rs);
        } finally {
            IOUtil.close(rs);
        }
    }

    private String buildAfterImageSQL(TableMeta tableMeta, TableRecords beforeImage) throws SQLException {
        SQLUpdateRecognizer recognizer = (SQLUpdateRecognizer)sqlRecognizer;
        List<String> updateColumns = recognizer.getUpdateColumns();
        StringBuilder prefix = new StringBuilder("SELECT ");
        if (!containsPK(updateColumns)) {
            // PK should be included.
            prefix.append(getColumnNameInSQL(tableMeta.getEscapePkName(getDbType()))).append(", ");
        }
        String suffix = " FROM " + getFromTableInSQL() + " WHERE " + buildWhereConditionByPKs(beforeImage.pkRows());
        StringJoiner selectSQLJoiner = new StringJoiner(", ", prefix.toString(), suffix);
        for (String column : updateColumns) {
            selectSQLJoiner.add(column);
        }
        return selectSQLJoiner.toString();
    }

}
