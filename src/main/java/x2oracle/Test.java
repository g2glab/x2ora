package x2oracle;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutionException;
import io.javalin.Javalin;
import oracle.pgql.lang.PgqlException;
import oracle.pgx.api.*;

// For testing - http://localhost:7000/node_match?node_ids=1

public class Test {
	public static void main(String[] szArgs) throws Exception {
		Javalin app = Javalin.create(config -> {
			config.enableCorsForAllOrigins();
		}).start(7000);
		app.get("/node_match/", ctx -> ctx.result(getResult(
			"node_match",
			ctx.queryParam("node_ids"))));
		app.get("/traversal/", ctx -> ctx.result(getResult(
			"traversal",
			ctx.queryParam("node_ids")//,
			//ctx.queryParam("min_hops"),
			//ctx.queryParam("max_hops")
			)));
		app.get("/cycle/", ctx -> ctx.result(getResult(
			"cycle",
			ctx.queryParam("node_ids"))));
		app.get("/compute/random_walk", ctx -> ctx.result(getResult(
			"compute-random_walk",
			ctx.queryParam("node_ids"))));
		app.get("/query/", ctx -> ctx.result(getResult(
			"query",
			ctx.queryParam("node_ids"))));
	}

	private static String getResult(String endpoint, String node_ids) {
		long time_start = System.nanoTime();
		String result = "";
		try {
			ServerInstance instance = Pgx.getInstance("http://localhost:7007");
			PgxSession session = instance.createSession("my-session");
			PgxGraph graph = session.getGraph("Cycle");
			
			String query;
			PgqlResultSet rs;
			
			long node_id = Long.parseLong(node_ids);

			switch (endpoint) {
			
			case "node_match":
				query = "SELECT n, m, e MATCH (n)-[e]->(m) WHERE ID(n) = " + node_id;
				rs = graph.queryPgql(query);
				result = getResultPG(rs, 2, 1, 0, 0, node_id);
				break;
			
			case "traversal":
				query = "SELECT DISTINCT"
						+ " ID(n0), LABEL(n0),"
						+ " ID(n1), LABEL(n1),"
						+ " ID(n2), LABEL(n2),"
						+ " ID(n3), LABEL(n3),"
						+ " ID(n4), LABEL(n4),"
						+ " ID(n5), LABEL(n5),"
						+ " ID(n6), LABEL(n6),"
						+ " ID(e1), ID(n0) AS e1s, ID(n1) AS e1d,"
						+ " ID(e2), ID(n1) AS e2s, ID(n2) AS e2d,"
						+ " ID(e3), ID(n2) AS e3s, ID(n3) AS e3d,"
						+ " ID(e4), ID(n3) AS e4s, ID(n4) AS e4d,"
						+ " ID(e5), ID(n4) AS e5s, ID(n5) AS e5d,"
						+ " ID(e6), ID(n5) AS e6s, ID(n6) AS e6d"
						+ " MATCH (n0)-[e1]->(n1)-[e2]->(n2)-[e3]->(n3)-[e4]->(n4)-[e5]->(n5)-[e6]->(n6)"
						+ " WHERE ID(n0) = " + node_id;
				rs = graph.queryPgql(query);
				result = getResultPG(rs, 7, 6, 0, 0, node_id);
				break;

			case "cycle":
				query = "SELECT ID(n), LABEL(n), ARRAY_AGG(ID(m)), ARRAY_AGG(ID(e))"
				+ " MATCH TOP 2 SHORTEST ((n) (-[e:transfer]->(m))* (n)) WHERE ID(n) = "
				+ node_id;
				rs = graph.queryPgql(query);
				result = getResultPG(rs, 1, 0, 1, 1, node_id);
				break;
			
			case "query":
				break;
			}
			
		} catch (ExecutionException e) {
			result = printException(e);
		} catch (InterruptedException e) {
			result = printException(e);
		} finally {
		}
		long time_end = System.nanoTime();
		System.out.println("Execution Time: " + (time_end - time_start)/1000/1000 + "ms");
		return result;		
	}
	
	private static String getResultPG(PgqlResultSet rs, int cnt_n, int cnt_e, int cnt_nl, int cnt_el, long node_id) {
		PgGraph pg = new PgGraph();
		pg.setName("test_graph");
		try {
			while (rs.next()) {

				int length_n = 2;
				int length_e = 3;
				int offset_e = cnt_n * length_n;
				int offset_nl = offset_e + (cnt_e * length_e);

				// Nodes
				for (int i = 1; i <= offset_e; i = i + length_n) {
					addNodeById(pg, rs.getLong(i), rs.getString(i + 1));
				}
				// Edges
				for (int i = offset_e + 1; i <= offset_nl; i = i + length_e) {
					addEdgeByIds(pg, rs.getLong(i), rs.getLong(i + 1), rs.getLong(i + 2), "transfer");
				}
				// Node List
				for (int i = offset_nl + 1; i <= offset_nl + cnt_nl; i++) {
					if(rs.getList(i) != null) {
						long node_src = node_id;
						long node_dst;
						long edge;
						for (int j = 0; j < rs.getList(i).size(); j++) {
							node_dst = (long) rs.getList(i).get(j);
							edge = (long) rs.getList(i + cnt_nl).get(j);
							addNodeById(pg, node_dst, "Account");
							addEdgeByIds(pg, edge, node_src, node_dst, "transfer");
							node_src = node_dst;
						}
					}
				}
			}
		} catch (PgqlException e) {
			e.printStackTrace();
		}
		return pg.exportJSON();
	}
  
	private static String printException(Exception e) {
		e.printStackTrace();
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		pw.flush();
		return sw.toString();
	}
  
	/*
	private static void addNode(PgGraph pg, PgxVertex<?> v) {
		long id = (long) v.getId();
		PgNode node = new PgNode(id);
		pg.addNode(id, node);
	}
	*/

	private static void addNodeById(PgGraph pg, long id, String label) {
		PgNode node = new PgNode(id);
		node.addLabel(label);
		pg.addNode(id, node);
	}

	/*
	private static void addEdge(PgGraph pg, PgxEdge e) {
		PgEdge edge = new PgEdge(
				(long) e.getSource().getId(),
				(long) e.getDestination().getId(),
				false
				);
		edge.addLabel(e.getLabel());
		pg.addEdge(edge);
	}
	*/
	
	private static void addEdgeByIds(PgGraph pg, long id, long id_s, long id_d, String label) {
		PgEdge edge = new PgEdge(
				id_s,
				id_d,
				false
				);
		edge.addLabel(label);
		pg.addEdge(id, edge);
	}
}