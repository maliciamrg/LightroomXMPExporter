package com.malicia.mrg.assistant.photo;

import com.adobe.internal.xmp.XMPConst;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.impl.XMPMetaImpl;
import com.adobe.internal.xmp.impl.XMPSerializerHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.*;

public class LightroomXMPExporter {

    public static void main(String[] args) throws SQLException {
        if (args.length != 1) {
            System.out.println("Usage: java -jar LightroomXMPExporter.jar <catalog.lrcat>");
            System.exit(1);
        }

        Statement stmt = null;

        String catalogPath = args[0];
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + catalogPath)) {
            String query = """
                SELECT 
                    agLibraryFile.idx_filename,
                    agLibraryFolder.pathFromRoot,
                    main.AgLibraryRootFolder.absolutePath,
                    adobe_images.rating,
                    adobe_images.pick,
                    adobe_images.captureTime,
                    (SELECT GROUP_CONCAT(DISTINCT agLibraryKeyword.name)
                     FROM agLibraryKeywordImage 
                     JOIN agLibraryKeyword 
                     ON agLibraryKeywordImage.tag = agLibraryKeyword.id_local
                     WHERE agLibraryKeywordImage.image = adobe_images.id_local) AS keywords
                FROM adobe_images
                JOIN agLibraryFile ON adobe_images.rootFile = agLibraryFile.id_local
                JOIN agLibraryFolder ON agLibraryFile.folder = agLibraryFolder.id_local
                JOIN agLibraryRootFolder ON agLibraryFolder.rootFolder = agLibraryRootFolder.id_local
                """;

            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);

            long nbWrote = 0;
            while (rs.next()) {
                String baseName = rs.getString("idx_filename");
                String folderPath = rs.getString("pathFromRoot");
                String absolutePath = rs.getString("absolutePath");
                Integer rating = rs.getInt("rating");
                Integer flag = rs.getInt("pick");
                String captureTime = rs.getString("captureTime");
                String keywords = rs.getString("keywords");

                File photoFile = new File(absolutePath + folderPath, baseName);
                if (!photoFile.exists() || keywords==null || keywords.isEmpty() || rating + flag == 0) {
                    System.err.println("Skipping file: " + photoFile);
                    continue;
                }

                File xmpFile = new File(photoFile.getAbsolutePath().replaceFirst("\\.[^.]+$", ".xmp"));
                writeXmp(xmpFile, rating, flag, captureTime, keywords);
                nbWrote++;
                System.out.printf("Wrote: %06d %s%n", nbWrote, xmpFile.getAbsolutePath());
            }
            System.out.printf("Wrote %06d xmp file", nbWrote);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            stmt.close();  // Multiple streams were opened. Only the last is closed.
        }
    }

    static void writeXmp(File file, Integer rating, Integer flag, String date, String tags) throws Exception {

        // Create the XMPMeta object
        XMPMeta xmpMeta = XMPMetaFactory.create();
        // Add custom fields to XMP (Label and Rating)
        Integer ratingLr = flag.equals(-1)?-1:rating;
        xmpMeta.setProperty(XMPConst.NS_XMP, "Rating", ratingLr);
        xmpMeta.setProperty(XMPConst.NS_XMP, "CreateDate", date);
        xmpMeta.setProperty(XMPConst.NS_DC, "subject", tags);

        // Write XMP metadata to file
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            XMPSerializerHelper xmpSerializerHelper = new XMPSerializerHelper();
            xmpSerializerHelper.serialize((XMPMetaImpl)xmpMeta, fileOutputStream,null);
        }

    }
}
