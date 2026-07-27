package ssg.pex.nosql.dialects.mongodb;

import ssg.pex.nosql.*;

import java.util.List;

/**
 * Internal executor that routes MongoDB operations to the underlying {@link InMemoryNoSqlDatabase}.
 * Primarily used by {@link MongoDbDatabase} to execute raw document commands.
 */
public final class MongodbExecutor {

    private final InMemoryNoSqlDatabase database;

    public MongodbExecutor(InMemoryNoSqlDatabase database) {
        this.database = database;
    }

    /**
     * Executes a command represented as a {@link Document} with a single key being the operation name.
     * Supported commands: insertOne, insertMany, find, findOne, updateOne, updateMany, deleteOne, deleteMany,
     * aggregate, count, distinct, createCollection, dropCollection.
     *
     * @param collectionName target collection
     * @param command        command document
     * @return result object (WriteResult, FindResult, List<Document>, Long, etc.)
     */
    @SuppressWarnings("unchecked")
    public Object execute(String collectionName, Document command) {
        if (command.size() != 1) {
            throw new NoSqlException("NOSQL_EXEC_ERROR", "Command document must have exactly one key");
        }
        String op = command.keySet().iterator().next();
        Object arg = command.get(op);

        NoSqlCollection col = database.getCollection(collectionName);

        return switch (op) {
            case "insertOne" -> col.insertOne((Document) arg);
            case "insertMany" -> col.insertMany((List<Document>) arg);
            case "find" -> arg == null ? col.find() : col.find((Document) arg);
            case "findOne" -> col.findOne((Document) arg);
            case "count" -> arg == null ? col.count() : col.count((Document) arg);
            case "updateOne" -> {
                List<Document> args = (List<Document>) arg;
                yield col.updateOne(args.get(0), args.get(1));
            }
            case "updateMany" -> {
                List<Document> args = (List<Document>) arg;
                yield col.updateMany(args.get(0), args.get(1));
            }
            case "deleteOne" -> col.deleteOne((Document) arg);
            case "deleteMany" -> col.deleteMany((Document) arg);
            case "aggregate" -> col.aggregate((List<Document>) arg);
            case "distinct" -> {
                List<?> args = (List<?>) arg;
                String field = (String) args.get(0);
                yield args.size() > 1 ? col.distinct(field, (Document) args.get(1)) : col.distinct(field);
            }
            case "drop" -> { col.drop(); yield WriteResult.none(); }
            default -> throw new NoSqlException("NOSQL_EXEC_ERROR", "Unknown command: " + op);
        };
    }
}
