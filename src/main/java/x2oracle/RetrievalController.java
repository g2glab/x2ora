package x2oracle;

import io.javalin.http.Handler;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import static x2oracle.Main.*;

import oracle.pg.rdbms.pgql.PgqlConnection;
import oracle.pg.rdbms.pgql.PgqlResultSet;
import oracle.pg.rdbms.pgql.PgqlPreparedStatement;
import oracle.pgql.lang.PgqlException;

public class RetrievalController {

  public static String countNodes() throws Exception {

    long timeStart = System.nanoTime();
    String result = "";
    try {
      
      // PG Schema
      /*
      PreparedStatement ps_pgs;
      ResultSet rs_pgs;
      ps_pgs = conn_pgs.prepareStatement("SELECT COUNT(v) FROM MATCH (v) ON " + strGraphPreset);
      ps_pgs.execute();
      rs_pgs = ps_pgs.getResultSet();
      */

      // PG View
      PgqlConnection pgqlConn = PgqlConnection.getConnection(conn);
      PgqlPreparedStatement ps = pgqlConn.prepareStatement("SELECT COUNT(v) FROM MATCH (v) ON " + strGraphPreset);
      PgqlResultSet rs = ps.executeQuery();

      if (rs.first()){
        result = "Test query succeeded.";
      }
    
    } catch (PgqlException e) {
      result = printException(e);
    }
    long timeEnd = System.nanoTime();
    System.out.println("INFO: Execution Time: " + (timeEnd - timeStart) / 1000 / 1000 + "ms (" + result + ")");
    return result;
  };
  
  public static Handler nodeMatch = ctx -> {

    String strGraph = ctx.queryParam("graph", strGraphPreset);
    String strIds = ctx.queryParam("node_ids[]", "");
    String strLabels = ctx.queryParam("node_labels[]", "").toUpperCase();
    String strLimit = ctx.queryParam("limit", "1000");
    
    String strWhere = " WHERE 1 = 1";
    if (!strIds.isEmpty()) {
      strWhere = strWhere + " AND v.id = '" + strIds + "'"; // Should be replaced to IN () in 21.3 
    }
    if (!strLabels.isEmpty()) {
      strWhere = strWhere + " AND LABEL(v) = '" + strLabels + "'"; // Should be replaced to IN () in 21.3
    }
    String clauseLimit = " LIMIT " + strLimit;
    String strQuery = "SELECT v.id, LABEL(v), v.props FROM MATCH (v) ON " + strGraph + strWhere + clauseLimit;    
    System.out.println("INFO: A request is received: " + strQuery);

    long timeStart = System.nanoTime();
    String result = "";
    PgGraph pg = new PgGraph();
    try {
      PgqlConnection pgqlConn = PgqlConnection.getConnection(conn);
      PgqlPreparedStatement ps = pgqlConn.prepareStatement(strQuery);
      ps.execute();
      PgqlResultSet rs = ps.getResultSet();
      result = "Nodes with ID [" + strIds + "] are retrieved.";
      pg = getResultPG(rs, 1, 0);
    } catch (PgqlException e) {
      result = printException(e);
    }
    long timeEnd = System.nanoTime();
    System.out.println("INFO: Execution time: " + (timeEnd - timeStart) / 1000 / 1000 + "ms (" + result + ")");
    ctx.result(result);
    ctx.contentType("application/json");
    HashMap<String, Object> response = new HashMap<>();
    response.put("request", ctx.fullUrl());
    response.put("pg", pg);
    ctx.json(response);
  };

  public static Handler edgeMatch = ctx -> {

    String strGraph = ctx.queryParam("graph", strGraphPreset);
    String strLabels = ctx.queryParam("edge_labels[]", "").toUpperCase();
    String strLimit = ctx.queryParam("limit", "1000");

    String strWhere = " WHERE 1 = 1";
    if (!strLabels.isEmpty()) {
      strWhere = strWhere + " AND LABEL(e) = '" + strLabels + "'"; // Should be replaced to IN () in 21.3
    }
    String clauseLimit = " LIMIT " + strLimit;
    String strQuery = "SELECT v1.id AS v1_id, LABEL(v1) AS v1_label, v1.props AS v1_props, v2.id AS v2_id, LABEL(v2) AS v2_label, v2.props AS v2_props, ID(e), v1.id AS src, v2.id AS dst, LABEL(e), e.props FROM MATCH (v1)-[e]->(v2) ON " + strGraph + strWhere + clauseLimit;
    System.out.println("INFO: A request is received: " + strQuery);

    long timeStart = System.nanoTime();
    String result = "";
    PgGraph pg = new PgGraph();
    try {
      PgqlConnection pgqlConn = PgqlConnection.getConnection(conn);
      PgqlPreparedStatement ps = pgqlConn.prepareStatement(strQuery);
      ps.execute();
      PgqlResultSet rs = ps.getResultSet();
      result = "Edge(s) with Label = " + strLabels + " are retrieved.";
      pg = getResultPG(rs, 2, 1);
    } catch (PgqlException e) {
      result = printException(e);
    }
    long timeEnd = System.nanoTime();
    System.out.println("INFO: Execution time: " + (timeEnd - timeStart) / 1000 / 1000 + "ms (" + result + ")");
    ctx.result(result);
    ctx.contentType("application/json");
    HashMap<String, Object> response = new HashMap<>();
    response.put("request", ctx.fullUrl());
    response.put("pg", pg);
    ctx.json(response);
  };

  public static Handler shortest = ctx -> {

    String strGraph = ctx.queryParam("graph", strGraphPreset);
    String strFromNodeId = ctx.queryParam("from_node_id", "");
    String strToNodeId = ctx.queryParam("to_node_id", "");
    String strTopK = ctx.queryParam("top_k", "1");
    String strLimit = ctx.queryParam("limit", "1000");

    String strWhere = " WHERE ID(v1) = '" + strFromNodeId + "' AND ID(v2) = '" + strToNodeId + "'";
    String clauseLimit = " LIMIT " + strLimit;
    String strQuery = "SELECT ARRAY_AGG(ID(e)) AS edges FROM MATCH TOP " + strTopK + " SHORTEST ((v1) (-[e]->(v))* (v2)) ON " + strGraph + strWhere + clauseLimit;
    System.out.println("INFO: A request is received: " + strQuery);

    long timeStart = System.nanoTime();
    String result = "";
    PgGraph pg = new PgGraph();
    try {
      PreparedStatement ps = conn.prepareStatement(strQuery);
      ps.execute();
      ResultSet rs = ps.getResultSet();
      result = "Edge(s) are retrieved.";
      while (rs.next()) {
        System.out.println(rs.getString(1));
      }
    } catch (SQLException e) {
      result = printException(e);
    }

    long timeEnd = System.nanoTime();
    System.out.println("INFO: Execution time: " + (timeEnd - timeStart) / 1000 / 1000 + "ms (" + result + ")");
    ctx.result(result);
    ctx.contentType("application/json");
    HashMap<String, Object> response = new HashMap<>();
    response.put("request", ctx.fullUrl());
    response.put("pg", pg);
    ctx.json(response);
  };

	private static PgGraph getResultPG(PgqlResultSet rs, int countNode, int countEdge) {
		PgGraph pg = new PgGraph();
		try {
			while (rs.next()) {

				int lengthNode = 3; // ID + Label + JSON Props
				int lengthEdge = 4; // ID + Src Node ID + Dst Node ID + Label + JSON Props

				int offsetEdge = countNode * lengthNode; // Edge Offset
				int offsetNodeList = offsetEdge + (countEdge * lengthEdge); // Node List Offset
        
				// Nodes
				for (int i = 1; i <= offsetEdge; i = i + lengthNode) {
					Object id = rs.getObject(i);
          if (!pg.hasNodeId(id)) {
            String label = rs.getString(i + 1);
            String props = rs.getString(i + 2);
            PgNode node = new PgNode(id, label, props);
            pg.addNode(node);
          }
				}
				// Edges
				for (int i = offsetEdge + 1; i <= offsetNodeList; i = i + lengthEdge) {
					String idSrc = rs.getString(i + 1);
          String idDst = rs.getString(i + 2);
          boolean undirected = false;
          String label = rs.getString(i + 3);
          String props = rs.getString(i + 4);
          PgEdge edge = new PgEdge(idSrc, idDst, undirected, label, props);
          pg.addEdge(edge);
				}
			}
		} catch (PgqlException e) {
			e.printStackTrace();
		}
		return pg;
	}
}