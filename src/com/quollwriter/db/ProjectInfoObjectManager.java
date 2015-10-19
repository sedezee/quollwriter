package com.quollwriter.db;

import java.io.*;
import java.util.*;
import java.sql.*;
import javax.imageio.*;
import java.awt.image.*;

import org.bouncycastle.bcpg.*;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.bc.*;
import org.bouncycastle.crypto.generators.*;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.openpgp.operator.bc.*;

import com.quollwriter.*;
import com.quollwriter.ui.*;
import com.quollwriter.db.*;
import com.quollwriter.data.*;
import com.quollwriter.data.editors.*;
import com.quollwriter.editors.messages.*;

public class ProjectInfoObjectManager extends ObjectManager
{

    public ProjectInfoObjectManager ()
    {
        
        this.handlers.put (ProjectInfo.class,
                           new ProjectInfoDataHandler (this));
        
    }

    public void init (File   dir,
                      String username,
                      String password,
                      String filePassword,
                      int    newSchemaVersion)
               throws GeneralException
    {

        super.init (dir,
                    username,
                    password,
                    filePassword,
                    newSchemaVersion);

    }

    public void updateLinks (NamedObject d,
                             Set<Link>   newLinks)
    {
        
    }

    public void deleteLinks (NamedObject n,
                             Connection  conn)
    {
        
    }
    
    public void getLinks (NamedObject d,
                          Project     p,
                          Connection  conn)
    {
        
    }
    
    public void updateSchemaVersion (int        newVersion,
                                     Connection conn)
                              throws Exception
    {

        List params = new ArrayList ();
        params.add (newVersion);

        this.executeStatement ("UPDATE info SET schema_version = ?",
                               params,
                               conn);
        
    }
    
    public int getSchemaVersion ()
                          throws GeneralException    
    {
        
        Connection c = null;

        try
        {

            c = this.getConnection ();

            PreparedStatement ps = c.prepareStatement ("SELECT schema_version FROM info");

            ResultSet rs = ps.executeQuery ();

            if (rs.next ())
            {

                return rs.getInt (1);

            }
            
        } catch (Exception e)
        {

            this.throwException (c,
                                 "Unable to get schema version",
                                 e);
                
        } finally
        {

            this.releaseConnection (c);

        }
            
        return -1;
        
        
    }
            
    public String getSchemaFile (String file)
    {
        
        return Constants.PROJECT_INFO_SCHEMA_DIR + file;
        
    }
    
    public String getCreateViewsFile ()
    {
        
        return Constants.PROJECT_INFO_UPDATE_SCRIPTS_DIR + "/create-views.xml";
        
    }
    
    public String getUpgradeScriptFile (int oldVersion,
                                        int newVersion)
    {
        
        return Constants.PROJECT_INFO_UPDATE_SCRIPTS_DIR + "/" + oldVersion + "-" + newVersion + ".xml";
        
    }
    
}