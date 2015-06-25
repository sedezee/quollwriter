package com.quollwriter.editors;

import java.io.*;
import java.awt.event.*;
import java.awt.image.*;
import java.net.*;
import java.util.*;
import java.sql.*;
import javax.swing.*;
import javax.swing.event.*;

import com.gentlyweb.xml.*;
import com.gentlyweb.properties.*;

import org.bouncycastle.openpgp.*;

import com.quollwriter.*;
import com.quollwriter.data.*;
import com.quollwriter.events.*;
import com.quollwriter.db.*;
import com.quollwriter.ui.*;
import com.quollwriter.ui.components.QPopup;
import com.quollwriter.ui.components.HyperlinkAdapter;
import com.quollwriter.data.editors.*;
import com.quollwriter.editors.messages.*;
import com.quollwriter.editors.ui.*;

public class EditorsEnvironment
{
        
    private static EditorsObjectManager editorsManager = null;
    private static EditorsWebServiceHandler editorsHandler = null;
    private static EditorsMessageHandler messageHandler = null;
    private static EditorAccount editorAccount = null;
    //private static boolean hasRegisteredForEditorService = false;
    public static boolean serviceAvailable = true;
    private static List<EditorEditor> editors = new ArrayList ();
    private static int    schemaVersion = 0;
    private static com.gentlyweb.properties.Properties editorsProps = new com.gentlyweb.properties.Properties ();
    private static EditorEditor.OnlineStatus currentOnlineStatus = EditorEditor.OnlineStatus.offline;
    private static EditorEditor.OnlineStatus lastOnlineStatus = null;
    
    private static Map<EditorChangedListener, Object> editorChangedListeners = null;
    private static Map<EditorMessageListener, Object> editorMessageListeners = null;
    private static Map<UserOnlineStatusListener, Object> userStatusListeners = null;
    private static Map<EditorInteractionListener, Object> editorInteractionListeners = null;
    
    private static boolean startupLoginTried = false;
    
    // Just used in the maps above as a placeholder for the listeners.
    private static final Object listenerFillObj = new Object ();
    
    // We create the listener containers here to allow others to listen for events even though the service
    // may not be running or ever available.  This is sort of wasteful but prevents clients having to bother
    // about checking for the service being available, they just won't receive events.
    static
    {
        
        // We use a synchronized weak hash map here so that we don't have to worry about all the
        // references since they will be transient compared to the potential length of the service
        // running.
        
        // Where possible listeners should de-register as normal but this just ensure that objects
        // that don't have a controlled pre-defined lifecycle (as opposed say to AbstractSideBar)
        // won't leak.
        EditorsEnvironment.editorChangedListeners = Collections.synchronizedMap (new WeakHashMap ());
        
        EditorsEnvironment.editorMessageListeners = Collections.synchronizedMap (new WeakHashMap ());

        EditorsEnvironment.userStatusListeners = Collections.synchronizedMap (new WeakHashMap ());

        EditorsEnvironment.editorInteractionListeners = Collections.synchronizedMap (new WeakHashMap ());

    }
        
    public static void logEditorMessages (boolean v)
    {
        
        EditorsEnvironment.messageHandler.logMessages (v);        
        
    }
    
    public static File getUserEditorsPropertiesFile ()
    {

        return Environment.getUserFile (Constants.EDITORS_PROPERTIES_FILE_NAME);

    }        
        
    public static void init ()
                      throws Exception
    {
        
        if (!EditorsEnvironment.serviceAvailable)
        {
            
            return;
            
        }
            
        // Get the user editor properties.
        File edPropsFile = EditorsEnvironment.getUserEditorsPropertiesFile ();

        if (edPropsFile.exists ())
        {

            com.gentlyweb.properties.Properties eprops = new com.gentlyweb.properties.Properties (edPropsFile,
                                                                                                  Environment.GZIP_EXTENSION);
                    
            EditorsEnvironment.editorsProps = eprops;
            
        }
            
        EditorsEnvironment.editorsProps.setParentProperties (Environment.getUserProperties ());
            
        try
        {

            EditorsEnvironment.schemaVersion = Integer.parseInt (Environment.getResourceFileAsString (Constants.EDITORS_SCHEMA_VERSION_FILE).trim ());

        } catch (Exception e)
        {

            Environment.logError ("Unable to read editors schema version file",
                                  e);
            
            EditorsEnvironment.serviceAvailable = false;
            
            return;

        }
        
        EditorsEnvironment.editorsHandler = new EditorsWebServiceHandler ();
        EditorsEnvironment.editorsHandler.init ();
        
        EditorsEnvironment.messageHandler = new EditorsMessageHandler ();
        EditorsEnvironment.messageHandler.init ();
        
        String eddir = EditorsEnvironment.editorsProps.getProperty (Constants.QW_EDITORS_DB_DIR_PROPERTY_NAME);
        
        if (eddir != null)
        {
            
            File dir = new File (eddir);
            
            if ((dir.exists ())
                &&
                (dir.isDirectory ())
               )
            {
                
                EditorsEnvironment.initDB (dir);
             
            }
            
        }

        // Bit of spelunking anyone???
        Environment.addStartupProgressListener (new PropertyChangedListener ()
        {
           
            public void propertyChanged (PropertyChangedEvent ev)
            {

                if ((Environment.isStartupComplete ())
                    &&
                    (!EditorsEnvironment.startupLoginTried)
                   )
                {
                    
                    if (EditorsEnvironment.getEditorsPropertyAsBoolean (Constants.QW_EDITORS_SERVICE_LOGIN_AT_QW_START_PROPERTY_NAME))
                    {
                     
                        UIUtils.doLater (new ActionListener ()
                        {
                           
                            public void actionPerformed (ActionEvent ev)
                            {
                                
                                String pwd = EditorsEnvironment.getEditorsProperty (Constants.QW_EDITORS_SERVICE_PASSWORD_PROPERTY_NAME);

                                String email = null;
                                
                                if (EditorsEnvironment.editorAccount != null)
                                {
                                    
                                    email = EditorsEnvironment.editorAccount.getEmail ();
                                    
                                }

                                // Add a notification to the project viewer saying we are logging in.
                                final AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                                
                                final Notification n = viewer.addNotification ("Logging in to the Editors service...",
                                                                               Constants.EDITORS_ICON_NAME,
                                                                               30);
                                
                                EditorsEnvironment.setLoginCredentials (email,
                                                                        pwd);
                                
                                EditorsEnvironment.startupLoginTried = true;
                                
                                EditorsEnvironment.goOnline (null,
                                                             new ActionListener ()
                                                             {
                                                                
                                                                public void actionPerformed (ActionEvent ev)
                                                                {
                                                                    
                                                                    viewer.removeNotification (n);
                                                                                                                                        
                                                                }
                                                                
                                                             },
                                                             // On cancel
                                                             new ActionListener ()
                                                             {
                                                                
                                                                public void actionPerformed (ActionEvent ev)
                                                                {
                                                                    
                                                                    viewer.removeNotification (n);
                                                                    
                                                                }
                                                                
                                                             },
                                                             // On error
                                                             new ActionListener ()
                                                             {
                                                                
                                                                public void actionPerformed (ActionEvent ev)
                                                                {
                                                                    
                                                                    EditorsEnvironment.setLoginCredentials (EditorsEnvironment.editorAccount.getEmail (),
                                                                                                            null);
                                                                    
                                                                    viewer.removeNotification (n);
                                                                    
                                                                    EditorsUIUtils.showLoginError ("Unable to automatically login, please check your email and password.",
                                                                                                   new ActionListener ()
                                                                                                   {
                                                                                                    
                                                                                                        public void actionPerformed (ActionEvent ev)
                                                                                                        {
                                                                                                            
                                                                                                            EditorsEnvironment.goOnline (null,
                                                                                                                                         null,
                                                                                                                                         null,
                                                                                                                                         null);
                                                                                                            
                                                                                                        }
                                                                                                    
                                                                                                   },
                                                                                                   null);
                                                                                                                                        
                                                                }
                                                                
                                                             });
                                
                            }
                            
                        });
                        
                    }
                    
                }
                

            }
            
        });
        
    }

    public static void removeEditorInteractionListener (EditorInteractionListener l)
    {
        
        EditorsEnvironment.editorInteractionListeners.remove (l);
        
    }
    
    public static void addEditorInteractionListener (EditorInteractionListener l)
    {
        
        EditorsEnvironment.editorInteractionListeners.put (l,
                                                           EditorsEnvironment.listenerFillObj);
        
    }

    public static void fireEditorInteractionEvent (final EditorEditor              ed,
                                                   final InteractionMessage.Action action)
    {
        
        UIUtils.doActionLater (new ActionListener ()
        {
        
            public void actionPerformed (ActionEvent aev)
            {
                
                Set<EditorInteractionListener> ls = null;
                
                EditorInteractionEvent ev = new EditorInteractionEvent (ed,
                                                                        action);
                
                // Get a copy of the current valid listeners.
                synchronized (EditorsEnvironment.editorInteractionListeners)
                {
                                    
                    ls = new LinkedHashSet (EditorsEnvironment.editorInteractionListeners.keySet ());
                    
                }
                    
                for (EditorInteractionListener l : ls)
                {
                    
                    l.handleInteraction (ev);
                    
                }

            }
            
        });
        
    }
    
    public static void removeUserOnlineStatusListener (UserOnlineStatusListener l)
    {
        
        EditorsEnvironment.userStatusListeners.remove (l);
        
    }
    
    public static void addUserOnlineStatusListener (UserOnlineStatusListener l)
    {
        
        EditorsEnvironment.userStatusListeners.put (l,
                                                    EditorsEnvironment.listenerFillObj);
        
    }

    private static void fireUserOnlineStatusChanged (final EditorEditor.OnlineStatus status)
    {
        
        UIUtils.doActionLater (new ActionListener ()
        {
        
            public void actionPerformed (ActionEvent aev)
            {
                
                Set<UserOnlineStatusListener> ls = null;
                
                UserOnlineStatusEvent ev = new UserOnlineStatusEvent (status);
                
                // Get a copy of the current valid listeners.
                synchronized (EditorsEnvironment.userStatusListeners)
                {
                                    
                    ls = new LinkedHashSet (EditorsEnvironment.userStatusListeners.keySet ());
                    
                }
                    
                for (UserOnlineStatusListener l : ls)
                {
                    
                    l.userOnlineStatusChanged (ev);
                    
                }

            }
            
        });
        
    }
    
    public static void setDefaultUserOnlineStatus ()
    {
    
        EditorEditor.OnlineStatus status = null;
    
        String defOnlineStatus = EditorsEnvironment.getEditorsProperty (Constants.QW_EDITORS_SERVICE_DEFAULT_ONLINE_STATUS_PROPERTY_NAME);
        
        if (defOnlineStatus != null)
        {
        
            try
            {
        
                status = EditorEditor.OnlineStatus.valueOf (defOnlineStatus);
                
            } catch (Exception e) {
                
                Environment.logError ("Unable to set default online status to: " +
                                      defOnlineStatus,
                                      e);
                
            }
            
        }
        
        if (status == null)
        {
            
            status = EditorEditor.OnlineStatus.online;
            
        }
        
        try
        {            
            
            EditorsEnvironment.setUserOnlineStatus (status);
                
        } catch (Exception e) {
                        
            Environment.logError ("Unable to set default online status to: " +
                                  status,
                                  e);

        }
        
    }
    
    public static void setUserOnlineStatus (EditorEditor.OnlineStatus status)
                                     throws Exception
    {
        
        if (!EditorsEnvironment.messageHandler.isLoggedIn ())
        {
            
            throw new IllegalStateException ("Cant set user online status if they are not logged in.");
            
        }
        
        if (EditorsEnvironment.currentOnlineStatus == status)
        {
            
            return;
            
        }
        
        EditorsEnvironment.currentOnlineStatus = status;
        
        // Send the presence.
        EditorsEnvironment.messageHandler.setOnlineStatus (status);
        
        EditorsEnvironment.fireUserOnlineStatusChanged (EditorsEnvironment.currentOnlineStatus);
        
    }
    
    public static EditorEditor.OnlineStatus getUserOnlineStatus ()
    {
        
        return EditorsEnvironment.currentOnlineStatus;
        
    }
    
    public static boolean isUserLoggedIn ()
    {
        
        return EditorsEnvironment.messageHandler.isLoggedIn ();
        
    }
    
    public static boolean isEditorsDBDir (File dir)
    {
        
        if (dir == null)
        {
            
            return false;
            
        }
        
        if (dir.isFile ())
        {
            
            return false;
            
        }
        
        if (!dir.exists ())
        {
            
            return false;
            
        }
        
        File f = new File (dir, Constants.EDITORS_DB_FILE_NAME_PREFIX + ".h2.db");
        
        if ((f.exists ())
            &&
            (f.isFile ())
           )
        {
            
            return true;
            
        }
        
        return false;
        
    }
    
    public static void initDB (File   dir)
                               throws Exception
    {
                                
        if (EditorsEnvironment.editorsProps == null)
        {
            
            throw new IllegalStateException ("Editors properties has not yet been set, try init(Properties) first.");
                                
        }
        
        // Get the username and password.
        String username = EditorsEnvironment.editorsProps.getProperty (Constants.DB_USERNAME_PROPERTY_NAME);
        String password = EditorsEnvironment.editorsProps.getProperty (Constants.DB_PASSWORD_PROPERTY_NAME);
        
        EditorsEnvironment.editorsManager = new EditorsObjectManager ();
        
        EditorsEnvironment.editorsManager.init (new File (dir, Constants.EDITORS_DB_FILE_NAME_PREFIX),
                                                username,
                                                password,
                                                null,
                                                EditorsEnvironment.getSchemaVersion ());

        // Create a file that indicates that the directory can be deleted.
        Utils.createQuollWriterDirFile (dir);
        
        EditorsEnvironment.editorAccount = EditorsEnvironment.editorsManager.getUserAccount ();

        if (EditorsEnvironment.editorAccount != null)
        {
            
            // Load up the editors.
            EditorsEnvironment.editors = (List<EditorEditor>) EditorsEnvironment.editorsManager.getObjects (EditorEditor.class,
                                                                                                            null,
                                                                                                            null,
                                                                                                            true);
            
        }
        
    }
    
    public static boolean hasUserSentAProjectBefore ()
                                              throws Exception
    {
        
        if (EditorsEnvironment.editorsManager == null)
        {
            
            return false;
            
        }
        
        return EditorsEnvironment.editorsManager.hasUserSentAProjectBefore ();
        
    }
    
    public static void fireEditorMessageEvent (final EditorMessageEvent ev)
    {
        
        UIUtils.doActionLater (new ActionListener ()
        {
        
            public void actionPerformed (ActionEvent aev)
            {
                
                Set<EditorMessageListener> ls = null;
                                
                // Get a copy of the current valid listeners.
                synchronized (EditorsEnvironment.editorMessageListeners)
                {
                                    
                    ls = new LinkedHashSet (EditorsEnvironment.editorMessageListeners.keySet ());
                    
                }
                    
                for (EditorMessageListener l : ls)
                {
                    
                    l.handleMessage (ev);

                }

            }
            
        });
                
    }
    
    public static NewProjectMessage getNewProjectMessage (EditorEditor ed,
                                                          String       projectId,
                                                          boolean      sentByMe)
                                                   throws Exception
    {
        
        return EditorsEnvironment.editorsManager.getNewProjectMessage (ed,
                                                                       projectId,
                                                                       sentByMe);
        
    }
    
    public static boolean hasMyPublicKeyBeenSentToEditor (EditorEditor ed)
                                                   throws Exception
    {
                                
        return EditorsEnvironment.editorsManager.hasMyPublicKeyBeenSentToEditor (ed);

    }
    
    public static boolean hasSentMessageOfTypeToEditor (EditorEditor ed,
                                                        String       messageType)
                                                 throws Exception
    {
        
        return EditorsEnvironment.editorsManager.hasSentMessageOfTypeToEditor (ed,
                                                                               messageType);
        
    }
    
    public static boolean hasEditorSentInfo (EditorEditor ed)
                                      throws Exception
    {
        
        if (ed.messagesLoaded ())
        {
            
            return ed.hasSentInfo ();
            
        }
        
        return EditorsEnvironment.editorsManager.hasEditorSentInfo (ed);
        
    }
    
    public static void removeEditorMessageListener (EditorMessageListener l)
    {
        
        EditorsEnvironment.editorMessageListeners.remove (l);
        
    }
    
    public static void addEditorMessageListener (EditorMessageListener l)
    {
        
        EditorsEnvironment.editorMessageListeners.put (l,
                                                       EditorsEnvironment.listenerFillObj);
        
    }        
    
    public static void fireEditorChangedEvent (EditorEditor ed,
                                               int          changeType)
    {
        
        EditorsEnvironment.fireEditorChangedEvent (new EditorChangedEvent (ed,
                                                                           changeType));
                
    }

    public static void fireEditorChangedEvent (final EditorChangedEvent ev)
    {
                
        UIUtils.doActionLater (new ActionListener ()
        {
        
            public void actionPerformed (ActionEvent aev)
            {
                
                Set<EditorChangedListener> ls = null;
                                
                // Get a copy of the current valid listeners.
                synchronized (EditorsEnvironment.editorChangedListeners)
                {
                                    
                    ls = new LinkedHashSet (EditorsEnvironment.editorChangedListeners.keySet ());
                    
                }
                    
                for (EditorChangedListener l : ls)
                {
                    
                    l.editorChanged (ev);

                }

            }
            
        });
                        
    }

    public static void removeEditorChangedListener (EditorChangedListener l)
    {
        
        EditorsEnvironment.editorChangedListeners.remove (l);
        
    }
    
    public static void addEditorChangedListener (EditorChangedListener l)
    {
        
        EditorsEnvironment.editorChangedListeners.put (l,
                                                       EditorsEnvironment.listenerFillObj);
        
    }    
    
    public static boolean isEditorsServiceAvailable ()
    {
        
        return EditorsEnvironment.serviceAvailable;
        
    }
        
    public static void setUserInformation (String        name,
                                           BufferedImage avatarImage)
                                    throws GeneralException
    {
        
        if (EditorsEnvironment.editorsManager == null)
        {
            
            throw new IllegalStateException ("Editor object manager not inited.");
            
        }
        
        if (avatarImage != null)
        {
                
            if (avatarImage.getWidth () > 300)
            {
                
                avatarImage = UIUtils.getScaledImage (avatarImage,
                                                      300);
                
            }
        
        }
        
        EditorsEnvironment.editorAccount.setName (name);
        EditorsEnvironment.editorAccount.setAvatar (avatarImage);

        EditorsEnvironment.editorsManager.setUserInformation (EditorsEnvironment.editorAccount);
        
    }
    
    public static int getSchemaVersion ()
    {
        
        return EditorsEnvironment.schemaVersion;
        
    }
    
    public static EditorAccount getUserAccount ()
    {
        
        return EditorsEnvironment.editorAccount;
        
    }
        
    public static EditorsMessageHandler getMessageHandler ()
    {
        
        return EditorsEnvironment.messageHandler;
        
    }

        public static Set<String> getWritingGenres ()
    {
        
        String gt = EditorsEnvironment.editorsProps.getProperty (Constants.WRITING_GENRES_PROPERTY_NAME);
        
        Set<String> gitems = new LinkedHashSet ();
        
        StringTokenizer t = new StringTokenizer (gt,
                                                 ",");
        
        while (t.hasMoreTokens ())
        {
            
            gitems.add (t.nextToken ().trim ());
            
        }
        
        return gitems;
        
    }
    
    public static EditorsWebServiceHandler getEditorsWebServiceHandler ()
    {
        
        return EditorsEnvironment.editorsHandler;
        
    }

    public static Set<EditorMessage> getAllUndealtWithMessages ()
                                                         throws Exception
    {
        
        if (EditorsEnvironment.editorsManager == null)
        {
            
            return null;
            
        }
        
        return EditorsEnvironment.editorsManager.getAllUndealtWithMessages ();
        
    }
    
    public static Set<EditorMessage> getProjectMessages (String    projId,
                                                         String... messageTypes)
                                                  throws Exception
    {
        
        return EditorsEnvironment.editorsManager.getProjectMessages (projId,
                                                                     messageTypes);
        
    }

    /**
     * Get a count of all the messages the user hasn't dealt with.
     *
     * @return The count.
     */
    public static int getUndealtWithMessageCount ()
                                           throws Exception
    {
       
        if (EditorsEnvironment.editorsManager == null)
        {
            
            return 0;
            
        }
       
        return EditorsEnvironment.editorsManager.getUndealtWithMessageCount (); 
        
    }
    
    public static File getEditorsAuthorAvatarImageFile (String suffix)
    {

        if (suffix == null)
        {
            
            return null;
                
        }
        
        File f = new File (Environment.getUserQuollWriterDir ().getPath () + "/" + Constants.EDITORS_AUTHOR_AVATAR_IMAGE_FILE_NAME_PREFIX + "." + suffix);

        f.getParentFile ().mkdirs ();

        return f;
        
    }

    public static File getEditorsAuthorAvatarImageFile ()
    {
        
        if (EditorsEnvironment.editorsHandler == null)
        {
            
            return null;
            
        }
        
        if (EditorsEnvironment.editorAccount == null)
        {
            
            return null;
            
        }

        EditorAuthor au = EditorsEnvironment.editorAccount.getAuthor ();
        
        if (au == null)
        {
            
            return null;
            
        }
                
        String t = EditorsEnvironment.editorsProps.getProperty (Constants.EDITORS_AUTHOR_AVATAR_IMAGE_FILE_TYPE_PROPERTY_NAME);
                        
        return EditorsEnvironment.getEditorsAuthorAvatarImageFile (t);
        
    }

    public static File getEditorsAuthorFile ()
    {
        
        File f = new File (Environment.getUserQuollWriterDir ().getPath () + "/" + Constants.EDITORS_AUTHOR_FILE_NAME);
        
        f.getParentFile ().mkdirs ();
        
        return f;
        
    }
        
    public static File getEditorsEditorAvatarImageFile (String suffix)
    {

        if (suffix == null)
        {
            
            return null;
                
        }
        
        File f = new File (Environment.getUserQuollWriterDir ().getPath () + "/" + Constants.EDITORS_EDITOR_AVATAR_IMAGE_FILE_NAME_PREFIX + "." + suffix);

        f.getParentFile ().mkdirs ();

        return f;
        
    }

    public static File getEditorsEditorAvatarImageFile ()
    {
        
        if (EditorsEnvironment.editorsHandler == null)
        {
            
            return null;
            
        }
        
        if (EditorsEnvironment.editorAccount == null)
        {
            
            return null;
            
        }

        EditorEditor ed = EditorsEnvironment.editorAccount.getEditor ();
        
        if (ed == null)
        {
            
            return null;
            
        }
                
        String t = EditorsEnvironment.editorsProps.getProperty (Constants.EDITORS_EDITOR_AVATAR_IMAGE_FILE_TYPE_PROPERTY_NAME);
                        
        return EditorsEnvironment.getEditorsEditorAvatarImageFile (t);
        
    }

    public static File getEditorsEditorFile ()
    {
        
        File f = new File (Environment.getUserQuollWriterDir ().getPath () + "/" + Constants.EDITORS_EDITOR_FILE_NAME);
        
        f.getParentFile ().mkdirs ();
        
        return f;
        
    }

    public static void clearUserPassword ()
    {
        
        if (EditorsEnvironment.editorAccount != null)
        {

            EditorsEnvironment.editorAccount.setPassword (null);
        
        }        
        
    }
    
    public static void getInvite (EditorEditor            editor,
                                  EditorsWebServiceAction onComplete,
                                  EditorsWebServiceAction onError)
    {    

        EditorsEnvironment.editorsHandler.getInvite (editor,
                                                     onComplete,
                                                     onError);    
    
    }
    
    public static void getInvite (String                  fromEmail,
                                  EditorsWebServiceAction onComplete,
                                  EditorsWebServiceAction onError)
    {
        
        EditorsEnvironment.editorsHandler.getInvite (fromEmail,
                                                     onComplete,
                                                     onError);
        
    }
    
    public static void deleteMessages (Set<EditorMessage> messages)
                                throws Exception
    {
        
        // TODO: Clean up the Set/List mess.
        EditorsEnvironment.editorsManager.deleteObjects (new ArrayList<EditorMessage> (messages),
                                                         null);
        
    }
    
    public static void deletePendingEditor (final EditorEditor          ed,
                                            final ActionListener        onDeleteComplete)
    {

        if (!ed.isPending ())
        {
            
            throw new IllegalStateException ("Only pending editors can be deleted using this method.");
            
        }
    
        final ActionListener doDelete = new ActionListener ()
        {
          
            public void actionPerformed (ActionEvent ev)
            {
                
                try
                {
                
                    EditorsEnvironment.editors.remove (ed);

                    EditorsEnvironment.editorsManager.deleteObject (ed,
                                                                    true,
                                                                    null);
                                    
                } catch (Exception e) {
                    
                    AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                    
                    UIUtils.showErrorMessage (viewer,
                                              "Unable to delete {editor} from local database.");
                    
                    Environment.logError ("Unable to delete editor: " +
                                          ed);
                    
                    return;
                    
                }
                
                // Fire an event.
                EditorsEnvironment.fireEditorChangedEvent (ed,
                                                           EditorChangedEvent.EDITOR_DELETED);
                
                if (onDeleteComplete != null)
                {
                    
                    onDeleteComplete.actionPerformed (new ActionEvent ("delete", 1, "delete"));
                    
                }                
                
            }
            
        };
    
        EditorsEnvironment.editorsHandler.deleteInvite (ed,
                                                        new EditorsWebServiceAction ()
        {
            
            public void processResult (EditorsWebServiceResult res)
            {
                
                doDelete.actionPerformed (new ActionEvent ("delete", 1, " delete"));
                
            }
        },
        new EditorsWebServiceAction ()
        {
            
            public void processResult (EditorsWebServiceResult res)
            {

                // If the invite couldn't be found then just delete the editor anyway since it's
                // probably been removed from the QW end.
                if (res.isNoDataFoundError ())
                {

                    doDelete.actionPerformed (new ActionEvent ("delete", 1, " delete"));

                } else {               
                      
                    EditorsEnvironment.editorsHandler.getDefaultEditorsWebServiceErrorAction ().processResult (res);

                }
                
            }
            
        });
        
    }
    
    public static void acceptInvite (final EditorEditor   editor,
                                     final EditorMessage  message,
                                     final ActionListener onComplete)
    {
        
        final String email = editor.getEmail ();
        
        EditorsEnvironment.editorsHandler.updateInvite (email,
                                                        Invite.Status.accepted,
                                                        new EditorsWebServiceAction ()
        {
            
            public void processResult (EditorsWebServiceResult res)
            {
                
                try
                {

                    editor.setEditorStatus (EditorEditor.EditorStatus.current);
        
                    EditorsEnvironment.updateEditor (editor);                
                    
                } catch (Exception e) {
                    
                    AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                    
                    UIUtils.showErrorMessage (viewer,
                                              "Unable to update {editor} information in local database.");
                    
                    Environment.logError ("Unable to update editor: " +
                                          editor +
                                          " to accepted",
                                          e);
                    
                    return;
                    
                }
                
                // Send an invite response message.
                EditorsEnvironment.getMessageHandler ().subscribeToEditor (editor);

                EditorsEnvironment.sendMessageToEditor (message,
                                                        onComplete,
                                                        null,
                                                        null);

            }
        },
        null);
        
    }
    
    public static void rejectInvite (final EditorEditor   editor,
                                     final EditorMessage  message,
                                     final ActionListener onComplete)
    {
        
        final String email = editor.getEmail ();
        
        EditorsEnvironment.editorsHandler.updateInvite (email,
                                                        Invite.Status.rejected,
                                                        new EditorsWebServiceAction ()
        {
            
            public void processResult (EditorsWebServiceResult res)
            {
                
                try
                {

                    editor.setEditorStatus (EditorEditor.EditorStatus.rejected);
        
                    EditorsEnvironment.updateEditor (editor);                
                    
                } catch (Exception e) {
                    
                    AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                    
                    UIUtils.showErrorMessage (viewer,
                                              "Unable to update {editor} information in local database.");
                    
                    Environment.logError ("Unable to update editor: " +
                                          editor +
                                          " to rejected",
                                          e);
                    
                    return;
                    
                }

                EditorsEnvironment.sendMessageToEditor (message,
                                                        onComplete,
                                                        null,
                                                        null);

            }
        },
        null);
        
    }

    public static void updateInvite (final String         email,
                                     final Invite.Status  newStatus,
                                     final ActionListener onUpdateComplete)
    {

        EditorsEnvironment.editorsHandler.updateInvite (email,
                                                        newStatus,
                                                        new EditorsWebServiceAction ()
        {
            
            public void processResult (EditorsWebServiceResult res)
            {

                EditorEditor ed = EditorsEnvironment.getEditorByEmail (email);
                
                EditorEditor.EditorStatus status = null;
                
                if (newStatus == Invite.Status.accepted)
                {
                    
                    status = EditorEditor.EditorStatus.current;
                    
                } else {
                    
                    status = EditorEditor.EditorStatus.valueOf (newStatus.getType ());
                    
                }
                
                try
                {
                
                    if (ed != null)
                    {
    
                        ed.setEditorStatus (status);
    
                        EditorsEnvironment.updateEditor (ed);                
                                        
                    } else {
                
                        ed = new EditorEditor ();
                
                        ed.setEmail (email);
                        ed.setEditorStatus (status);
                
                        // Add to our editors.
                        EditorsEnvironment.addNewEditor (ed);
                        
                    }
                    
                } catch (Exception e) {
                    
                    AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                    
                    UIUtils.showErrorMessage (viewer,
                                              "Unable to update {editor} information in local database.");
                    
                    Environment.logError ("Unable to update editor: " +
                                          email +
                                          " to status: " +
                                          status,
                                          e);
                    
                    return;
                    
                }
                
                if (onUpdateComplete != null)
                {
                    
                    onUpdateComplete.actionPerformed (new ActionEvent ("update", 1, "update"));
                    
                }
                
            }
        },
        null);
        
    }
        
    public static void goOffline ()
    {
        
        EditorsEnvironment.editorsHandler.logout ();
        
        EditorsEnvironment.messageHandler.logout (null);
        
        EditorsEnvironment.currentOnlineStatus = EditorEditor.OnlineStatus.offline;
        
        EditorsEnvironment.fireUserOnlineStatusChanged (EditorsEnvironment.currentOnlineStatus);
        
        for (EditorEditor ed : EditorsEnvironment.editors)
        {
            
            ed.setOnlineStatus (null);
            
            EditorsEnvironment.fireEditorChangedEvent (new EditorChangedEvent (ed,
                                                                               EditorChangedEvent.EDITOR_CHANGED));            
            
        }
        
    }
        
    public static boolean isMessageSendInProgress ()
    {
        
        return EditorsEnvironment.messageHandler.isMessageSendInProgress ();
        
    }
    
    public static void updateUserPassword (final String newPassword)
    {
        
        final ActionListener onError = new ActionListener ()
        {

            @Override
            public void actionPerformed (ActionEvent ev)
            {
                
                UIUtils.showErrorMessage (Environment.getFocusedProjectViewer (),
                                          "Unable to update your password, please contact Quoll Writer support for assistance.");
                
            }
            
        };
        
        EditorsEnvironment.editorsHandler.changePassword (newPassword,
                                                          new EditorsWebServiceAction ()
        {
                                                                
            @Override
            public void processResult (EditorsWebServiceResult res)
            {
                                                          
                EditorsEnvironment.messageHandler.changePassword (newPassword,
                                                                  new ActionListener ()
                                                                  {
                                                                    
                                                                      @Override
                                                                      public void actionPerformed (ActionEvent ev)
                                                                      {
                                                                    
                                                                        UIUtils.showMessage ((PopupsSupported) Environment.getFocusedProjectViewer (),
                                                                                             "Password updated",
                                                                                             "Your password has been updated.");
                                                                        
                                                                      }
                                                                    
                                                                  },
                                                                  onError);
                                                                    
            }
            
        },
        new EditorsWebServiceAction ()
        {

            @Override
            public void processResult (EditorsWebServiceResult res)
            {

                onError.actionPerformed (new ActionEvent ("error", 1, ""));
            
            }
            
        });
        
    }
    
    private static void checkForUndealtWithMessages ()
    {
        
        final AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();

        if (!viewer.isEditorsSideBarVisible ())
        {
        
            int c = 0;
            
            try
            {
                
                c = EditorsEnvironment.getUndealtWithMessageCount ();
                
            } catch (Exception e) {
                
                Environment.logError ("Unable to get undealt with message count",
                                      e);
                
                return;
                
            }
        
            if (c > 0)
            {
                                                                                                            
                String s = "There are <b>%s</b> {Editor} messages requiring your attention.";
                
                if (c == 1)
                {
                    
                    s = "There is <b>1</b> {Editor} message that requires your attention.";
                    
                }
                
                final Notification n = viewer.addNotification (String.format (s + "  <a href='action:showundealtwitheditormessages'>Click here to view the message(s).</a>",
                                                                              c),
                                                               Constants.EDITORS_ICON_NAME,
                                                               6000);
                
                // TODO: Find a nicer way of getting rid of clickable notifications.
                // Bit of a risk this...
                JTextPane tp = (JTextPane) n.getContent ();
                
                tp.addHyperlinkListener (new HyperlinkAdapter ()
                {
    
                    public void hyperlinkUpdate (HyperlinkEvent ev)
                    {
                   
                        if (ev.getEventType () == HyperlinkEvent.EventType.ACTIVATED)
                        {
                   
                            viewer.removeNotification (n);
                            
                        }
                        
                    }
                    
                });
                                                                                                            
            }
            
        }
        
    }
    
    private static void startMessageNotificationThread ()
    {
        
        // Start the listener for messages.
        // Display a notification is there are undealt with messages.
        // Only show if the editors side bar isn't visible.
        Thread t = new Thread (new Runnable ()
        {
            
            public void run ()
            {
                                                                                            
                while (EditorsEnvironment.editorAccount != null)
                {
                
                    try
                    {
                        
                        EditorsEnvironment.checkForUndealtWithMessages ();
                        
                        Thread.sleep (10 * 60 * 1000);
                        
                    } catch (Exception e) {

                        Environment.logError ("Unable to get undealt with messages",
                                              e);
                            
                        break;
                        
                    }
                    
                }
                
            }
            
        });
        
        t.setName ("editors-service-check-for-undealt-with-messages");
        t.setPriority (Thread.MIN_PRIORITY);
        t.setDaemon (true);
        
        t.start ();
        
    }
    
    public static void goOnline (final String         loginReason,
                                 final ActionListener onLogin,
                                 final ActionListener onCancel,
                                 final ActionListener onError)
    {
        
        final AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
        
        final String reason = (loginReason != null ? loginReason : "To go online you must first login.");

        ActionListener login = new ActionListener ()
        {

            public void actionPerformed (ActionEvent ev)
            {
                
                EditorsEnvironment.editorsHandler.login (new ActionListener ()
                                                         {
                                                            
                                                            public void actionPerformed (ActionEvent ev)
                                                            {
                                                                                                                                                        
                                                                EditorsEnvironment.messageHandler.login (new ActionListener ()
                                                                                                         {
                                                                                                                
                                                                                                            public void actionPerformed (ActionEvent ev)
                                                                                                            {
                                                                                                                    
                                                                                                                EditorsUIUtils.hideLogin ();
                                                                                                                
                                                                                                                try
                                                                                                                {
                                                                                                                
                                                                                                                    EditorsEnvironment.setEditorsProperty (Constants.QW_EDITORS_SERVICE_HAS_LOGGED_IN_PROPERTY_NAME,
                                                                                                                                                           true);
                                                                                                                    
                                                                                                                } catch (Exception e) {
                                                                                                                    
                                                                                                                    Environment.logError ("Unable to set property",
                                                                                                                                          e);
                                                                                                                                                                                                                            
                                                                                                                }
                                                                                                                
                                                                                                                if (EditorsEnvironment.editorAccount.getLastLogin () == null)
                                                                                                                {
                                                                                                                    
                                                                                                                    EditorsEnvironment.getEditorsWebServiceHandler ().checkPendingInvites ();
                                                                                                                    
                                                                                                                }                                                                                                                                    
                                                                                                                
                                                                                                                EditorsEnvironment.setDefaultUserOnlineStatus ();
                                                                                                                   
                                                                                                                //EditorsEnvironment.currentOnlineStatus = EditorEditor.OnlineStatus.online;                                                                                                                        
                                                                                                        
                                                                                                                //EditorsEnvironment.fireUserOnlineStatusChanged (EditorsEnvironment.currentOnlineStatus);
                                                                                                                                                                                                                                        
                                                                                                                java.util.Date d = new java.util.Date ();
                                                                                                                    
                                                                                                                EditorsEnvironment.editorAccount.setLastLogin (d);
                                                                                                                
                                                                                                                try
                                                                                                                {
                                                                                                                
                                                                                                                    EditorsEnvironment.editorsManager.setLastLogin (d);
                                                                                                                    
                                                                                                                } catch (Exception e) {
                                                                                                                    
                                                                                                                    Environment.logError ("Unable to set last login date",
                                                                                                                                          e);
                                                                                                                    
                                                                                                                }
                                                                                                                
                                                                                                                EditorsEnvironment.startMessageNotificationThread ();
                                                                                                                                                                                                                                    
                                                                                                                if (onLogin != null)
                                                                                                                {
                                                                                                                                                                                                                                
                                                                                                                    onLogin.actionPerformed (ev);
                                                                                                                                                                                                                                            
                                                                                                                }
                                                                                                                    
                                                                                                            }
                                                                                                                
                                                                                                        },
                                                                                                        onError);
                                                                
                                                            }
                                                            
                                                         },
                                                         onError);
                
            }
            
        };
        
        if (EditorsEnvironment.hasLoginCredentials ())
        {
 
            login.actionPerformed (new ActionEvent ("login", 1, "login"));
            
        } else {
        
            EditorsUIUtils.showLogin (viewer,
                                      reason,
                                      login,
                                      onCancel);

        }
                
    }
    
    public static void sendMessageToAllEditors (String                loginReason,
                                                EditorMessage         mess,
                                                ActionListener        onSend,
                                                ActionListener        onLoginCancel,
                                                ActionListener        onError)
    {
                
        for (EditorEditor ed : EditorsEnvironment.getEditors ())
        {
            
            EditorsEnvironment.messageHandler.sendMessage (loginReason,
                                                           mess,
                                                           ed,
                                                           onSend,
                                                           onLoginCancel,
                                                           onError);
            
        }
        
    }

    public static void sendUserInformationToAllEditors (ActionListener        onSend,
                                                        ActionListener        onLoginCancel,
                                                        ActionListener        onError)
    {
        
        String loginReason = "To send your information to your {editors} you must first login to the Editors service.";
        
        EditorsEnvironment.sendMessageToAllEditors (loginReason,
                                                    new EditorInfoMessage (EditorsEnvironment.getUserAccount ()),
                                                    onSend,
                                                    onLoginCancel,
                                                    onError);
        
    }
    
    public static void sendUserInformationToEditor (EditorEditor          ed,
                                                    ActionListener        onSend,
                                                    ActionListener        onLoginCancel,
                                                    ActionListener        onError)
    {
        
        EditorsEnvironment.messageHandler.sendMessage ("To send your information to <b>" + ed.getMainName () + "</b> you must first login to the Editors service.",
                                                       new EditorInfoMessage (EditorsEnvironment.getUserAccount ()),
                                                       ed,
                                                       onSend,
                                                       onLoginCancel,
                                                       onError);        
        
    }
    
    public static void sendInteractionMessageToEditor (InteractionMessage.Action action,
                                                       EditorEditor              ed,
                                                       ActionListener            onSend)
    {
        
        if (!EditorsEnvironment.messageHandler.isLoggedIn ())
        {
            
            return;
            
        }

        // Only send if editor is online.
        if (ed.isOffline ())
        {
            
            return;
            
        }
        
        EditorsEnvironment.sendMessageToEditor (new InteractionMessage (action,
                                                                        ed),
                                                onSend,
                                                null,
                                                null);
        
    }
    
    public static void sendMessageToEditor (final EditorMessage         mess,
                                            final ActionListener        onSend,
                                            final ActionListener        onLoginCancel,
                                            final ActionListener        onError)
    {

        EditorsEnvironment.messageHandler.sendMessage ("To send a message to <b>" + mess.getEditor ().getMainName () + "</b> you must first login to the Editors service.",
                                                       mess,
                                                       mess.getEditor (),
                                                       onSend,
                                                       onLoginCancel,
                                                       onError);
                
    }
        
    public static void sendInvite (final String toEmail)
    {
        
        EditorsEnvironment.editorsHandler.sendInvite (toEmail,
                                                      new EditorsWebServiceAction ()
        {
                                                                
            public void processResult (EditorsWebServiceResult res)
            {

                AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                
                final EditorEditor ed = new EditorEditor ();

                // Add the invite to our local editors.
                try
                {
                    
                    ed.setEmail (toEmail.trim ().toLowerCase ());
                    ed.setInvitedByMe (true);
                    
                    //String resS = res.getReturnObjectAsString ();
                    
                    Invite inv = Invite.createFrom ((Map) res.getReturnObject ());
                    /*
                    if (resS != null)
                    {
                        
                        // Need a better way of handling this.
                        if (!res.isSuccess ())
                        {
                        
                            // This is the public key of the editor.
                            ed.setTheirPublicKey (EditorsUtils.convertToPGPPublicKey (Base64.decode (resS)));

                        }
                        
                    }
                    */
                    
                    ed.setTheirPublicKey (inv.getToPublicKey ());
                    ed.setMessagingUsername (inv.getToMessagingUsername ());
                    ed.setServiceName (inv.getToServiceName ());
                    
                    EditorsEnvironment.addNewEditor (ed);

                    // If they have a public key then send an invite message.
                    if (ed.getTheirPublicKey () != null)
                    {
                        
                        ActionListener onCancel = new ActionListener ()
                        {
                           
                            private boolean inviteSent = false;
                           
                            public void actionPerformed (ActionEvent ev)
                            {
                                                    
                                if (this.inviteSent)
                                {
                                    
                                    return;
                                                                                           
                                }
                                
                                this.inviteSent = true;
                                
                                InviteMessage invite = new InviteMessage (EditorsEnvironment.editorAccount);
                                
                                invite.setEditor (ed);
                                
                                // Send an invite.
                                EditorsEnvironment.sendMessageToEditor (invite,
                                                                        new ActionListener ()
                                                                        {
                                                                            
                                                                            public void actionPerformed (ActionEvent ev)
                                                                            {
                                                                                
                                                                                AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                                                                                
                                                                                UIUtils.showMessage (viewer,
                                                                                                     "Invite sent",
                                                                                                     "An invite has been sent to: <b>" + toEmail + "</b>.",
                                                                                                     "Ok, got it",
                                                                                                     null);                                                                                                            
                                                                                
                                                                            }
                                                                            
                                                                        },
                                                                        null,
                                                                        null);
                               
                           }
                           
                        };
                        
                        // Ask the user if they want to send the editor their project.
                        QPopup p = UIUtils.createQuestionPopup (viewer,
                                                     "Send {project} / {chapters}?",
                                                     Constants.SEND_ICON_NAME,
                                                     String.format ("Would you like to send your {project}/{chapters} to <b>%s</b> now?",
                                                                    ed.getMainName ()),
                                                     "Yes",
                                                     "No, not now",
                                                     new ActionListener ()
                                                     {
                                                        
                                                        public void actionPerformed (ActionEvent ev)
                                                        {
                                                            
                                                            AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                                                            
                                                            EditorsUIUtils.showSendProject (viewer,
                                                                                            ed,
                                                                                            null);

                                                        }
                                                        
                                                     },
                                                     onCancel,
                                                     null,
                                                     null);
                        
                        p.getHeader ().getControls ().setVisible (false);
                        
                        return;
                        
                    }
                    
                } catch (Exception e) {
                    
                    Environment.logError ("Unable to add new editor to local",
                                          e);
                                        
                    // Show an error.
                    // Can't uninvite, what to do?
                    UIUtils.showErrorMessage (viewer,
                                              "An internal error has occurred while saving the invite to local storage.  Please contact Quoll Writer support for assistance.");
                    
                    return;
                    
                }

                UIUtils.showMessage (viewer,
                                     "Invite sent",
                                     String.format ("An invite has been sent to: <b>%s</b>.",
                                                    toEmail),
                                     "Ok, got it",
                                     null);
                                                
            }
        
        },
        null);
        
    }
    
    public static boolean hasLoginCredentials ()
    {
        
        if (EditorsEnvironment.editorAccount == null)
        {
            
            return false;
            
        }
        
        return ((EditorsEnvironment.editorAccount.getEmail () != null)
                &&
                (EditorsEnvironment.editorAccount.getPassword () != null));
    
    }
    
    public static void setLoginCredentials (String email,
                                            String password)
    {
                
        if (EditorsEnvironment.editorAccount == null)
        {
            
            throw new IllegalStateException ("No account available.");
                
        }
                
        EditorsEnvironment.editorAccount.setEmail (email);
        EditorsEnvironment.editorAccount.setPassword (password);
        
    }
        
    public static void initUserCredentials (String        email,
                                            String        password,
                                            String        serviceName,
                                            String        messagingUsername,
                                            PGPPublicKey  publicKey,
                                            PGPPrivateKey privateKey)
                                     throws GeneralException
    {
        
        if (EditorsEnvironment.editorAccount != null)
        {
            
            throw new GeneralException ("Already have an editor account");
            
        }
                
        EditorsEnvironment.editorAccount = new EditorAccount ();
        
        EditorsEnvironment.editorAccount.setEmail (email);
        EditorsEnvironment.editorAccount.setPassword (password);
        EditorsEnvironment.editorAccount.setPublicKey (publicKey);
        EditorsEnvironment.editorAccount.setPrivateKey (privateKey);
        EditorsEnvironment.editorAccount.setServiceName (serviceName);
        EditorsEnvironment.editorAccount.setMessagingUsername (messagingUsername);

        EditorsEnvironment.editorsManager.setUserInformation (EditorsEnvironment.editorAccount);
        
    }

    public static boolean hasRegistered ()
    {
        
        return EditorsEnvironment.editorAccount != null;
        
    }

    public synchronized static void loadMessagesForEditor (EditorEditor ed)
                                                    throws GeneralException
    {

        if (ed == null)
        {
            
            throw new NullPointerException ("Expected an editor");
            
        }
    
        if (ed.messagesLoaded ())
        {
            
            return;
            
        }
 
        synchronized (ed)
        {
    
            // Oh java why you so kooky...
            ed.setMessages (new LinkedHashSet<EditorMessage> ((List<EditorMessage>) EditorsEnvironment.editorsManager.getObjects (EditorMessage.class,
                                                                                                            ed,
                                                                                                            null,
                                                                                                            true)));

        }
        
    }
    
    public static void sendNewProjectResponse (final NewProjectMessage mess,
                                               final boolean           accepted,
                                               final String            responseMessage)
                                        throws GeneralException
    {
        
        NewProjectResponseMessage res = new NewProjectResponseMessage (mess.getForProjectId (),
                                                                       accepted,
                                                                       responseMessage,
                                                                       mess.getEditor ());
        
        res.setDealtWith (true);
        
        // For now just return rejected.
        EditorsEnvironment.sendMessageToEditor (res,
                                                new ActionListener ()
                                                {
                                                    
                                                    public void actionPerformed (ActionEvent ev)
                                                    {
                                                        
                                                        try
                                                        {
                                                        
                                                            mess.setAccepted (accepted);
                                                            mess.setDealtWith (true);
                                                            mess.setResponseMessage (responseMessage);
                                                    
                                                            // Update the original message.
                                                            EditorsEnvironment.updateMessage (mess);                                                        

                                                        } catch (Exception e) {
                                                            
                                                            UIUtils.showErrorMessage (null,
                                                                                      "Unable to update {project} message, please contact Quoll Writer support for assistance.");
                                                            
                                                            Environment.logError ("Unable to update new project message: " +
                                                                                  mess,
                                                                                  e);
                                                            
                                                        }
                                                        
                                                    }
                                                    
                                                },
                                                null,
                                                null);                                                                            
        
    }
    
    public static void sendError (EditorMessage          mess,
                                  ErrorMessage.ErrorType errorType,
                                  String                 reason)
    {
        
        try
        {
            
            EditorsEnvironment.sendMessageToEditor (new ErrorMessage (mess,
                                                                      errorType,
                                                                      reason),
                                                    null,
                                                    null,
                                                    null);

        } catch (Exception e) {
            
            Environment.logError ("Unable to send error message for message: " +
                                  mess.getMessageId () +
                                  " to editor: " +
                                  mess.getEditor () +
                                  " with error type: " +
                                  errorType.getType (),
                                  e);
            
        }
        
    }
    
    public static Map getOriginalMessageAsMap (EditorMessage m)
                                        throws Exception
    {
        
        if (m.isSentByMe ())
        {
            
            throw new IllegalArgumentException ("Not supported for messages sent by me.");
            
        }
        
        byte[] bytes = EditorsEnvironment.editorsManager.getOriginalMessage (m);
                
        // Decrypt first.
        try
        {

            bytes = EditorsUtils.decrypt (bytes,
                                          EditorsEnvironment.editorAccount.getPrivateKey (),
                                          m.getEditor ().getTheirPublicKey ());            

        } catch (Exception e) {
            
            throw new GeneralException ("Unable to decrypt message from editor: " +
                                        m.getEditor (),
                                        e);
            
        }
            
        String messageData = null;
            
        try
        {
        
            messageData = new String (bytes,
                                      "utf-8");

        } catch (Exception e) {
            
            throw new GeneralException ("Unable to convert decrypted message to a string from editor: " +
                                        m.getEditor (),
                                        e);
            
        }
                            
        // JSON decode
        Map data = (Map) JSONDecoder.decode (messageData);
        
        return data;
        
    }
    
    public static ProjectEditor getProjectEditor (Project      p,
                                                  EditorEditor ed)
                                           throws GeneralException
    {
        
        if (EditorsEnvironment.editorAccount == null)
        {
            
            return null;
            
        }
        
        if (p == null)
        {
            
            return null;
            
        }
        
        if (ed == null)
        {
            
            return null;
            
        }
        
        List<ProjectEditor> pes = (List<ProjectEditor>) EditorsEnvironment.editorsManager.getObjects (ProjectEditor.class,
                                                                                                      p,
                                                                                                      null,
                                                                                                      false);        
        
        for (ProjectEditor pe : pes)
        {
            
            if (pe.getEditor () == ed)
            {
                
                return pe;
                
            }
            
        }
        
        return null;
        
    }
    
    public static List<ProjectEditor> getProjectEditors (String projectId)
                                                  throws GeneralException
    {
        
        if (EditorsEnvironment.editorAccount == null)
        {
            
            return null;
            
        }
        
        Project p = new Project ();
        p.setId (projectId);
        
        return (List<ProjectEditor>) EditorsEnvironment.editorsManager.getObjects (ProjectEditor.class,
                                                                                   p,
                                                                                   null,
                                                                                   false);        
        
    }
    /*
    public static String getUserPublicKeyBase64EncodedX ()
    {
        
        if (this.userPublicKey == null)
        {
            
            return null;
            
        }
        
        RSAPublicBCPGKey pubKey = (RSAPublicBCPGKey) this.myPublicKey.getPublicKeyPacket ().getKey ();
                
        return Base64.encodeBytes (pubKey.getEncoded ());
        
    }
    */
        
    public static void sendProjectEditStopMessage (final Project        p,
                                                   final ActionListener onComplete)
    {
        
        if (!p.getType ().equals (Project.EDITOR_PROJECT_TYPE))
        {
            
            throw new IllegalArgumentException ("Only editor projects can be deleted.");
            
        }
        
        // Send message saying no longer editing.
        ProjectEditStopMessage message = new ProjectEditStopMessage (p,
                                                                     null,
                                                                     p.getForEditor ());
        
        EditorsEnvironment.sendMessageToEditor (message,
                                                new ActionListener ()
                                                {
                                                    
                                                    public void actionPerformed (ActionEvent ev)
                                                    {
                                                                                                                
                                                        if (onComplete != null)
                                                        {
                                                            
                                                            UIUtils.doLater (onComplete);
                                                            
                                                        }
                                                        
                                                    }
                                                    
                                                },
                                                null,
                                                null);
        
    }
    
    public static void addProjectEditor (ProjectEditor pe)
                                    throws GeneralException
    {
        
        EditorsEnvironment.editorsManager.saveObject (pe,
                                                      null);

        // Fire an event.
        EditorsEnvironment.fireEditorChangedEvent (pe.getEditor (),
                                                   EditorChangedEvent.EDITOR_CHANGED);
                                                      
    }
    
    public static void removeProjectEditors (Project p)
                                      throws GeneralException
    {

        List<ProjectEditor> pes = EditorsEnvironment.getProjectEditors (p.getId ());
        
        /*
        // Damn what to do for the best here?
        for (ProjectEditor pe : pes)
        {
        
            EditorsEnvironment.removeProjectEditor (pe);                
            
        }
        */

        EditorsEnvironment.editorsManager.deleteObjects (pes,
                                                         null);
        
        for (ProjectEditor pe : pes)
        {
            
            // Fire an event.
            EditorsEnvironment.fireEditorChangedEvent (pe.getEditor (),
                                                       EditorChangedEvent.EDITOR_CHANGED);            
            
        }

    }
    
    public static void removeProjectEditor (ProjectEditor pe)
                                     throws GeneralException
    {
        
        EditorsEnvironment.editorsManager.deleteObject (pe,
                                                        false,
                                                        null);
/*
        if (pe.isAccepted ())
        {                                                        
        
            // Only send this if the editor has already accepted the project.
            ProjectEditStopMessage mess = new ProjectEditStopMessage (pe.getProject (),
                                                                      null,
                                                                      pe.getEditor ());
            
            EditorsEnvironment.sendMessageToEditor (mess);

        }
  */                                                      
        // Fire an event.
        EditorsEnvironment.fireEditorChangedEvent (pe.getEditor (),
                                                   EditorChangedEvent.EDITOR_CHANGED);
                                                      
    }

    public static Set<Project> getProjectsSentToEditor (EditorEditor ed)
                                                 throws Exception
    {
        
        if (EditorsEnvironment.editorsManager == null)
        {
            
            return null;
            
        }
        
        return EditorsEnvironment.editorsManager.getProjectsSentToEditor (ed);
        
    }
    
    public static Set<Project> getProjectsForEditor (EditorEditor ed)
                                              throws Exception
    {
        
        Set<Project> projs = new LinkedHashSet ();
        
        for (Project p : Environment.getAllProjects ())
        {
            
            if (p.getForEditor () == ed)
            {
                
                projs.add (p);
                
            }
            
        }
        
        return projs;
        
    }
    
    public static void setProjectEditorStatus (final String       projId,
                                               final EditorEditor ed,
                                               final String       newStatus)
                                        throws Exception
    {
        
        Project proj = null;
        
        try
        {
            
            proj = Environment.getProjectById (projId,
                                               Project.NORMAL_PROJECT_TYPE);
            
        } catch (Exception e) {
            
            throw new GeneralException ("Unable to get project for id: " +
                                        projId,
                                        e);
            
        }                        

        ProjectEditor pe = EditorsEnvironment.getProjectEditor (proj,
                                                                ed);
        
        if (pe == null)
        {
            
            throw new IllegalArgumentException ("Editor is not a project editor for project: " + projId + ", editor: " + ed);
            
        }
        
        pe.setStatusMessage (newStatus);
        
        EditorsEnvironment.updateProjectEditor (pe);

    }
    
    public static void removeEditor (final EditorEditor   ed,
                                     final ActionListener onComplete)
    {
                
        // Send the editor removed message
        EditorRemovedMessage mess = new EditorRemovedMessage (ed);
        
        EditorsEnvironment.sendMessageToEditor (mess,
                                                new ActionListener ()
                                                {
                                                    
                                                    public void actionPerformed (ActionEvent ev)
                                                    {
                                                                   
                                                        // Remove all projects for the editor.
                                                        Set<Project> edProjs = null;
                                                        
                                                        try
                                                        {
                                                            
                                                            edProjs = EditorsEnvironment.getProjectsForEditor (ed);
                                                            
                                                        } catch (Exception e) {
                                                            
                                                            Environment.logError ("Unable to get projects for editor: " +
                                                                                  ed,
                                                                                  e);
                                                            
                                                            AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();

                                                            UIUtils.showErrorMessage (viewer,
                                                                                      "Unable to get {projects} for {editor}, please contact Quoll Writer support for assistance.");
                                                            
                                                            return;
                                                            
                                                        }
                                                                                                             
                                                        try
                                                        {
                                                            
                                                            // Uupdate the editor to be previous.
                                                            ed.setEditorStatus (EditorEditor.EditorStatus.previous);
                                                        
                                                            EditorsEnvironment.updateEditor (ed);
                                                            
                                                        } catch (Exception e) {
                                                            
                                                            Environment.logError ("Unable to update editor: " +
                                                                                  ed,
                                                                                  e);
                                                            
                                                            AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();
                                                            
                                                            UIUtils.showErrorMessage (viewer,
                                                                                      "Unable to update {editor}, please contact Quoll Writer support for assistance.");
                                                            
                                                            return;
                                                            
                                                        }

                                                        // Unsubscribe.
                                                        EditorsEnvironment.messageHandler.unsubscribeFromEditor (ed);
                                                                                                                
                                                        for (Project p : edProjs)
                                                        {
                                                            
                                                            // Just to be sure.
                                                            if (!p.getType ().equals (Project.EDITOR_PROJECT_TYPE))
                                                            {
                                                                
                                                                continue;
                                                                
                                                            }
                                                            
                                                            AbstractProjectViewer pv = Environment.getProjectViewer (p);
                                                            
                                                            if (pv != null)
                                                            {
                                                                
                                                                pv.close (true,
                                                                          null);
                                                                
                                                            }
                                                            
                                                            Environment.deleteProject (p);                                                            
                                                            
                                                        }
                                                        
                                                        if (onComplete != null)
                                                        {
                                                            
                                                            UIUtils.doLater (onComplete);
                                                            
                                                        }
                                                        
                                                    }
                                                    
                                                },
                                                null,
                                                null);
   
    }
    
    public static EditorMessage getMessageByKey (int key)
                                          throws GeneralException
    {

        return (EditorMessage) (DataObject) EditorsEnvironment.editorsManager.getObjectByKey (EditorMessage.class,
                                                                                              key,
                                                                                              null, // parent object
                                                                                              null, // connection
                                                                                              true);

    }
    
    public static void addMessage (EditorMessage mess)
                            throws GeneralException
    {
                
        // TODO: This needs to be changed, ok for now.
        if (mess instanceof InteractionMessage)
        {
            
            return;
                
        }
        
        EditorsEnvironment.editorsManager.saveObject (mess,
                                                      null);

        mess.getEditor ().addMessage (mess);
                                                      
        EditorsEnvironment.fireEditorMessageEvent (new EditorMessageEvent (mess.getEditor (),
                                                                           mess,
                                                                           EditorMessageEvent.MESSAGE_ADDED));
        
    }

    public static void updateMessage (EditorMessage mess)
                         throws       GeneralException
    {
        
        EditorsEnvironment.editorsManager.saveObject (mess,
                                                      null);

        // Fire an event.
        EditorsEnvironment.fireEditorMessageEvent (new EditorMessageEvent (mess.getEditor (),
                                                                           mess,
                                                                           EditorMessageEvent.MESSAGE_CHANGED));
                                                      
    }

    public static void updateProjectEditor (ProjectEditor pe)
                               throws       GeneralException
    {
        
        EditorsEnvironment.editorsManager.saveObject (pe,
                                                      null);

        // Fire an event.
        EditorsEnvironment.fireEditorChangedEvent (pe.getEditor (),
                                                   EditorChangedEvent.EDITOR_CHANGED);
                                                      
    }
    
    public static void updateEditor (EditorEditor ed)
                        throws       GeneralException
    {
        
        EditorsEnvironment.editorsManager.saveObject (ed,
                                                      null);

        // Fire an event.
        EditorsEnvironment.fireEditorChangedEvent (ed,
                                                   EditorChangedEvent.EDITOR_CHANGED);
                                                      
    }
    
    public static void addNewEditor (EditorEditor ed)
                              throws GeneralException
    {

        EditorsEnvironment.editorsManager.saveObject (ed,
                                                      null);

        EditorsEnvironment.editors.add (ed);

        // Fire an event.
        EditorsEnvironment.fireEditorChangedEvent (ed,
                                                   EditorChangedEvent.EDITOR_ADDED);

    }

    public static EditorEditor getEditorByEmail (String em)
    {
        
        if (em == null)
        {
            
            return null;
            
        }
        
        em = em.toLowerCase ();
        
        for (EditorEditor ed : EditorsEnvironment.editors)
        {
            
            if ((ed.getEmail () != null)
                &&
                (ed.getEmail ().equals (em))
               )
            {
                
                return ed;
                
            }
            
        }
        
        return null;
        
    }
        
    public static EditorEditor getEditorByMessagingUsername (String u)
    {
        
        if (u == null)
        {
            
            return null;
            
        }
                
        for (EditorEditor ed : EditorsEnvironment.editors)
        {
            
            if ((ed.getMessagingUsername () != null)
                &&
                (ed.getMessagingUsername ().equals (u))
               )
            {
                
                return ed;
                
            }
            
        }
        
        return null;
        
    }

    public static EditorEditor getEditorByKey (long key)
    {
        
        if (key < 1)
        {
            
            return null;
            
        }
                
        for (EditorEditor ed : EditorsEnvironment.editors)
        {
            
            if (ed.getKey () == key)
            {
                
                return ed;
                
            }
            
        }
        
        return null;
        
    }

    /**
     * Return a count of the number of editors with a status of "pending".
     *
     * @return The count.
     */
    public static int getPendingEditorsCount ()
    {
       
        int c = 0;
       
        for (EditorEditor ed : EditorsEnvironment.editors)
        {
            
            if (ed.getEditorStatus () == EditorEditor.EditorStatus.pending)
            {
                
                c++;
                
            }
            
        }
        
        return c;
        
    }
    
    public static List<EditorEditor> getEditors ()
    {
        
        return EditorsEnvironment.editors;
        
    }

    public static void fireProjectEvent (ProjectEvent ev)
    {
        
        // We'll see?
        // Fire to all project viewers?
                
    }
 
    public static String getNewMessageId (EditorEditor ed,
                                          String       messageType)
                                   throws Exception
    {
                
        return EditorsEnvironment.editorsManager.getNewMessageId (ed,
                                                                  messageType);
                
    }

    /*
    public static com.gentlyweb.properties.Properties getUserEditorsProperties ()
    {

        return Environment.userEditorsProperties;

    }
*/
    public static void setEditorsProperty (String           name,
                                           AbstractProperty prop)
                                    throws Exception
    {
        
        EditorsEnvironment.editorsProps.setProperty (name,
                                                     prop);
        
        EditorsEnvironment.saveEditorsProperties (null);
        
    }

    public static void setEditorsProperty (String name,
                                           String value)
                                    throws Exception
    {
        
        EditorsEnvironment.editorsProps.setProperty (name,
                                                     new StringProperty (name,
                                                                         value));
        
        EditorsEnvironment.saveEditorsProperties (null);
        
    }
    
    public static void setEditorsProperty (String  name,
                                           boolean value)
                                    throws Exception
    {
        
        EditorsEnvironment.editorsProps.setProperty (name,
                                                     new BooleanProperty (name,
                                                                          value));
        
        EditorsEnvironment.saveEditorsProperties (null);
        
    }

    public static void removeEditorsProperty (String name)
                                       throws Exception
    {
    
        EditorsEnvironment.editorsProps.removeProperty (name);
        
        EditorsEnvironment.saveEditorsProperties (null);
        
    }
    
    public static void saveEditorsProperties (com.gentlyweb.properties.Properties props)
                                           throws Exception
    {

        if (props == null)
        {
            
            props = EditorsEnvironment.editorsProps;
            
        }

        // Load the per user properties.
        File pf = EditorsEnvironment.getUserEditorsPropertiesFile ();

        JDOMUtils.writeElementToFile (props.getAsJDOMElement (),
                                      pf,
                                      true);

    }

    public static boolean getEditorsPropertyAsBoolean (String name)
    {
        
        return EditorsEnvironment.editorsProps.getPropertyAsBoolean (name);
        
    }
    
    public static String getEditorsProperty (String name)
    {

        return EditorsEnvironment.editorsProps.getProperty (name);

    }
 
    public static boolean isShowPopupWhenNewMessageReceived ()
    {
        
        // Never show popups in full screen mode.
        if (Environment.isInFullScreen ())
        {
            
            return false;
            
        }
        
        return EditorsEnvironment.getEditorsPropertyAsBoolean (Constants.EDITORS_SHOW_POPUP_WHEN_NEW_MESSAGE_RECEIVED_PROPERTY_NAME);
     
    }

    public static void setShowPopupWhenNewMessageReceived (boolean v)
                                                    throws Exception
    {
    
        BooleanProperty prop = new BooleanProperty (Constants.EDITORS_SHOW_POPUP_WHEN_NEW_MESSAGE_RECEIVED_PROPERTY_NAME,
                                                    v);
        EditorsEnvironment.setEditorsProperty (Constants.EDITORS_SHOW_POPUP_WHEN_NEW_MESSAGE_RECEIVED_PROPERTY_NAME,
                                               prop);
        
    }
 
    public static void fullScreenEntered ()
    {
        
        // Get the current status.
        if (EditorsEnvironment.isUserLoggedIn ())
        {
            
            if (EditorsEnvironment.getEditorsPropertyAsBoolean (Constants.QW_EDITORS_SERVICE_SET_BUSY_ON_FULL_SCREEN_ENTERED_PROPERTY_NAME))
            {
                
                
                // Get the current status, if it's not "busy" then change it to busy.
                if (EditorsEnvironment.getUserOnlineStatus () != EditorEditor.OnlineStatus.busy)
                {

                    EditorsEnvironment.lastOnlineStatus = EditorsEnvironment.getUserOnlineStatus ();
                    
                    try
                    {
                    
                        EditorsEnvironment.setUserOnlineStatus (EditorEditor.OnlineStatus.busy);
                        
                    } catch (Exception e) {
                        
                        Environment.logError ("Unable to set user online status to busy",
                                              e);
                        
                    }
                    
                }
                
            }
            
        }

    }
    
    public static void fullScreenExited ()
    {

        if (EditorsEnvironment.lastOnlineStatus != null)
        {
            
            try
            {
            
                EditorsEnvironment.setUserOnlineStatus (EditorsEnvironment.lastOnlineStatus);
                
            } catch (Exception e) {
                
                Environment.logError ("Unable to set user online status to last: " +
                                      EditorsEnvironment.lastOnlineStatus,
                                      e);
                
            }
            
            EditorsEnvironment.lastOnlineStatus = null;
            
        }
        
        // Check for new messages and show a notification if there are any.
        EditorsEnvironment.checkForUndealtWithMessages ();
        
    }

    /**
     * Show a warning message if the editor is offline when the user is trying to send them a message.
     *
     * @param ed The editor the user is sending a message to.
     */
    public static void showMessageSendWarningIfEditorOfflineMessage (EditorEditor ed)
    {
        
        if (ed.isOffline ())
        {
        
            // TODO: Next release change this to be more in context.
            // Has the user seen this before?
            //if (!EditorsEnvironment.getEditorsPropertyAsBoolean (Constants.EDITORS_SEEN_OFFLINE_SEND_MESSAGE_PROPERTY_NAME))
            //{
                
                AbstractProjectViewer viewer = Environment.getFocusedProjectViewer ();

                UIUtils.showMessage ((PopupsSupported) viewer,
                                     "{Editor} is offline",
                                     String.format ("<b>%s</b> is currently offline.  Your message will be stored on the server (encrypted) until they log in again and retrieve the message.<br /><br />Note: this applies whenever you send a message to {an editor} that is offline.  This message won't be shown again.",
                                                    ed.getShortName ()));
                
                try
                {
                
                    EditorsEnvironment.setEditorsProperty (Constants.EDITORS_SEEN_OFFLINE_SEND_MESSAGE_PROPERTY_NAME,
                                                           true);

                } catch (Exception e) {
                    
                    Environment.logError ("Unable to set property",
                                          e);
                    
                }
                
            //}
            
        }
        
        
    }
 
    public static URL getReportMessageURL ()
                                    throws Exception
    {

        return new URL (Environment.getQuollWriterWebsite () + Environment.getProperty (Constants.EDITORS_SERVICE_REPORT_MESSAGE_PAGE_PROPERTY_NAME));
        
    }
 
    private static void deleteAllEditors (final Deque<EditorEditor> eds,
                                          final ActionListener      onComplete)
    {
        
        if (eds.size () == 0)
        {
            
            if (onComplete != null)
            {
                
                onComplete.actionPerformed (new ActionEvent ("deleted", 1, "deleted"));                
                
            }
            
        } else {
            
            EditorsEnvironment.removeEditor (eds.pop (),
                                             new ActionListener ()
                                             {
                                                
                                                public void actionPerformed (ActionEvent ev)
                                                {
                                                    
                                                    EditorsEnvironment.deleteAllEditors (eds,
                                                                                         onComplete);
                                                    
                                                }
                                                
                                             });
                            
        }
        
    }
 
    public static void deleteUserAccount (final ActionListener onComplete,
                                          final ActionListener onError)
    {
                                
        // Send EditorRemoved messages for all editors (but don't remove them).
        EditorsEnvironment.goOnline ("To delete your account you must first go online.",
                                     new ActionListener ()
        {
            
            public void actionPerformed (ActionEvent ev)
            {
                
                EditorsEnvironment.deleteAllEditors (new ArrayDeque (EditorsEnvironment.editors),
                                                     new ActionListener ()
                {
                                                
                    public void actionPerformed (ActionEvent ev)
                    {
                                                    
                        // Delete the account.
                        EditorsEnvironment.editorsHandler.deleteAccount (new EditorsWebServiceAction ()
                        {
        
                            public void processResult (EditorsWebServiceResult res)
                            {
                                
                                // Sign out.
                                EditorsEnvironment.goOffline ();
                                                            
                                // Remove saved values (if present).
                                try
                                {
                                    
                                    EditorsEnvironment.removeEditorsProperty (Constants.QW_EDITORS_SERVICE_PASSWORD_PROPERTY_NAME);
                                    
                                } catch (Exception e) {
                                    
                                    Environment.logError ("Unable to remove editors property",
                                                          e);
                                           
                                }
                                                            
                                // Close all the db connections.
                                EditorsEnvironment.editorsManager.closeConnectionPool ();
                                
                                EditorsEnvironment.editorAccount = null;
                                
                                try
                                {
                                    
                                    EditorsEnvironment.removeEditorsProperty (Constants.QW_EDITORS_DB_DIR_PROPERTY_NAME);
                        
                                } catch (Exception e) {
                                                                        
                                    Environment.logError ("Unable to remove editors database location",
                                                          e);
                                                                                    
                                }
                                
                                if (onComplete != null)
                                {
                                    
                                    onComplete.actionPerformed (new ActionEvent ("deleted", 1, "deleted"));
                                        
                                }                                                    
                                                            
                            }
                                                        
                        },
                        new EditorsWebServiceAction ()
                        {
                            
                            public void processResult (EditorsWebServiceResult res)
                            {
                                
                                Environment.logError ("Unable to delete all editors" + 
                                                      res);

                                if (onError != null)
                                {
                                    
                                    onError.actionPerformed (new ActionEvent (res, 1, "error"));
                                    
                                } else {
                                                                        
                                    UIUtils.showErrorMessage (Environment.getFocusedProjectViewer (),
                                                              "Unable to delete your account, please contact Quoll Writer support for assistance.");
                                    
                                }
                                
                            }
                            
                        });
                                                    
                    }
                                                
                });
                
            }
            
        },
        null,
        onError);

    }
    
}