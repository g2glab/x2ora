package x2oracle;

import io.javalin.http.Handler;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import static x2oracle.Main.*;

import oracle.pg.rdbms.pgql.PgqlConnection;
import oracle.pg.rdbms.pgql.PgqlResultSet;
import oracle.pgql.lang.PgqlException;
import oracle.pg.rdbms.pgql.PgqlPreparedStatement;

import java.util.UUID;

public class UpdateController {

  public static Handler mergeNode = ctx -> {
    long timeStart = System.nanoTime();

    String strGraph = ctx.formParam("graph", strGraphPreset);
    String strId = ctx.formParam("id");
    String strLabel = ctx.formParam("label");
    String strProps = ctx.formParam("props");

    String result = mergeNode(strGraph, strId, strLabel, strProps);
    conn.commit();
    long timeEnd = System.nanoTime();
    System.out.println("INFO: Execution Time: " + (timeEnd - timeStart) / 1000 / 1000 + "ms (" + result + ")");
    ctx.result(result + "\n");
  };

  public static Handler mergeEdge = ctx -> {
    long timeStart = System.nanoTime();

    String strGraph = ctx.formParam("graph", strGraphPreset);
    String strSrcId = ctx.formParam("src_id");
    String strDstId = ctx.formParam("dst_id");
    String strLabel = ctx.formParam("label");
    String strProps = ctx.formParam("props");

    String result = mergeEdge(strGraph, strSrcId, strDstId, strLabel, strProps);
    conn.commit();
    long timeEnd = System.nanoTime();
    System.out.println("INFO: Execution Time: " + (timeEnd - timeStart) / 1000 / 1000 + "ms (" + result + ")");
    ctx.result(result + "\n");
  };

  public static Handler mergeGraph = ctx -> {
    long timeStart = System.nanoTime();
    
    PgGraph pg = ctx.bodyAsClass(PgGraph.class);
    System.out.println("INFO: Graph received (" + pg.countNodes() + " nodes, " + pg.countEdges() + " edges).");

    for (PgNode node : pg.getNodes()) {
      String result = mergeNode(strGraphPreset, (String)node.getId(), node.getLabel(), node.getPropertiesJSON());
      System.out.println(result);
    }

    for (PgEdge edge : pg.getEdges()) {
      String result = mergeEdge(strGraphPreset, (String)edge.getFrom(), (String)edge.getTo(), edge.getLabel(), edge.getPropertiesJSON());
      System.out.println(result);
    }
    
    String result = "";
    conn.commit();
    long timeEnd = System.nanoTime();
    System.out.println("INFO: Execution Time: " + (timeEnd - timeStart) / 1000 / 1000 + "ms (" + result + ")");
    ctx.result(result + "\n");
  };

  private static String mergeNode(String strGraph, String strId, String strLabel, String strProps) throws SQLException {
    String result = "";
    try {
      Boolean able = true;

      // Check if the node exists
      if (able) {
        PgqlConnection pgqlConn = PgqlConnection.getConnection(conn);
        PgqlPreparedStatement pps = pgqlConn.prepareStatement("SELECT v.id FROM MATCH (v) ON " + strGraph + " WHERE v.label = ? AND v.id = '" + strId + "'");
        pps.setString(1, strLabel.toUpperCase());
        pps.setString(2, strId);
        pps.execute();
        PgqlResultSet rs = pps.getResultSet();
        if (rs.first()){
          able = false;
          result = "Node " + strLabel.toUpperCase() + " " + strId + " exists.";
        }
      }

      // Insert the node if not exists
      if (able) {
        String query = "INSERT INTO x2pgv_node VALUES (?, ?, ?, ?)";			
        try (PreparedStatement ps = conn.prepareStatement(query)) {
          ps.setString(1, strGraph.toUpperCase());
          ps.setString(2, strId);
          ps.setString(3, strLabel.toUpperCase());
          ps.setString(4, strProps);
          ps.execute();
          result = "Node " + strLabel.toUpperCase() + " " + strId + " is added.";  
        } catch (Exception e) {
          conn.rollback();
          System.out.println("rollback");
          throw e;
        };
      } else {
        // The update query below gets PgqlToSqlException so this operation is skipped now
        /*
        String query = "UPDATE v SET (v.json = ?) FROM MATCH (v) ON " + strLabel + " WHERE LABEL(v) = ? AND v.id = ?";			
        System.out.println(query);
        ps = pgqlConn.prepareStatement(query);
        ps.setString(1, strProps);
        ps.setString(2, strLabel.toUpperCase());
        ps.setString(3, strId);
        ps.execute();
        result = "Node " + strLabel + " " + strId + " is update.";
        */
      }
      
    } catch (PgqlException e) {
      result = printException(e);
    }
    return result;
  };

  private static String mergeEdge(String strGraph, String strSrcId, String strDstId, String strLabel, String strProps) throws SQLException {
    String result = "";
    try {
      
      Boolean able = true;
      PgqlConnection pgqlConn = PgqlConnection.getConnection(conn);

      // Check if the node exists
      if (able) {
        //ps = conn.prepareStatement("SELECT v.id FROM MATCH (v) ON " + strGraph + " WHERE v.id = ?");
        PgqlPreparedStatement pps = pgqlConn.prepareStatement("SELECT v.id FROM MATCH (v) ON " + strGraph + " WHERE v.id = ?");
        pps.setString(1, strSrcId);
        pps.execute();
        PgqlResultSet rs = pps.getResultSet();
        if (!rs.first()){
          able = false;
          result = "Node " + strSrcId + " does not exist.";
        }
      }

      // Check if the node exists
      if (able) {
        //ps = conn.prepareStatement("SELECT v.id FROM MATCH (v) ON " + strGraph + " WHERE v.id = ?");
        PgqlPreparedStatement pps = pgqlConn.prepareStatement("SELECT v.id FROM MATCH (v) ON " + strGraph + " WHERE v.id = ?");
        pps.setString(1, strDstId);
        pps.execute();
        PgqlResultSet rs = pps.getResultSet();
        if (!rs.first()){
          able = false;
          result = "Node " + strDstId + " does not exist.";
        }
      }

      // Check if the edge exists
      if (able) {
        //ps = conn.prepareStatement("SELECT e.id FROM MATCH (src)-[e]->(dst) ON " + strGraph + " WHERE e.label = ? AND src.id = ? AND dst.id = ?");
        PgqlPreparedStatement pps = pgqlConn.prepareStatement("SELECT e.id FROM MATCH (src)-[e]->(dst) ON " + strGraph + " WHERE e.label = ? AND src.id = ? AND dst.id = ?");
        pps.setString(1, strLabel.toUpperCase());
        pps.setString(2, strSrcId);
        pps.setString(3, strDstId);
        pps.execute();
        PgqlResultSet rs = pps.getResultSet();
        if (rs.first()){
          able = false;
          result = "Edge " + strLabel + " " + strSrcId + " -> " + strDstId + " exists.";
        }
      }

      // Insert the edge if not exists
      if (able) {
        String query = "INSERT INTO x2pgv_edge VALUES (?, ?, ?, ?, ?, ?)";			
        try (PreparedStatement ps = conn.prepareStatement(query)) {
          ps.setString(1, strGraph.toUpperCase());
          ps.setString(2, UUID.randomUUID().toString());
          ps.setString(3, strSrcId);
          ps.setString(4, strDstId);
          ps.setString(5, strLabel);
          ps.setString(6, strProps);
          ps.execute();
          result = "Edge " + strLabel + " " + strSrcId + " -> " + strDstId + " is added.";
        } catch (Exception e) {
          conn.rollback();
          System.out.println("rollback");
          throw e;
        };
      }
      
    } catch (PgqlException e) {
      result = printException(e);
    }
    return result;
  };
}