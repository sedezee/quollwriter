package com.quollwriter.editors.messages;

import java.util.*;

import com.quollwriter.*;
import com.quollwriter.data.*;
import com.quollwriter.data.editors.*;
import com.quollwriter.editors.*;

/**
 * The base class for all messages sent between editors.
 * {@link DataObject} is our base so that we get the db/key/version/id management for free.
 *
 * {@link getMessage()}/{@link setMessage(String)} are used to conver the message to and from a string
 * format suitable for saving to the db.  Each subclass defines it's own data structures for storing information about
 * the message.  This information is generally used in the UI.  These methods are not used for message sending.
 *
 * {@link toMap()} and {@link fillMap(Map)} are used to convert the information in this object to a map that can then
 * be json encoded and sent to the editor.  Sub-classes override {@link fillMap(Map)} to add their own information.
 *
 * {@link init(Map, EditorEditor)} and {@link doInit(Map, EditorEditor)} are used to take a json decoded message from
 * an editor and fill the object.  See {@link MessageFactory.getMessage (String, EditorEditor, boolean)} for details
 * of how the raw message received is converted to an EditorMessage.
 *
 * The original message is kept, encrypted if sent that way, in the message and stored in the db.  This is to keep
 * a record of what was sent/received, however the summary is also kept for easy access (decrypting and parsing out the chapter text
 * everytime you want it is wasteful and unnecessary).
 *
 * Each message has a unique id, see {@link getMessageId()}.  This is generated by
 * {@link EditorsObjectManager.getNewMessageId(EditorEditor,String)} and is guaranteed to be unique for the combination of
 * editor and message type.
 *
 * A message has a "dealt with" flag, when set this means that the user has acknowledged the message and/or dealt with it
 * in some way.  The exact meaning of "dealt with" is determined by sub-classes, for example a user being shown a chat message
 * means it has been dealt with but accepting a new project requires more interaction, such as pressing a confirmation button.
 *
 */
public abstract class EditorMessage extends DataObject 
{
    
    public static final String DEALT_WITH = "dealtwith";
    public static final String OBJECT_TYPE = "message";
        
    protected EditorEditor editor = null;
    private String forProjectId = null;
    private String forProjectName = null;
    private String origMessage = null;
    private boolean sentByMe = false;
    private Date when = new Date ();
    private String messageId = null;
    private boolean wantsAck = false;
    private long ackKey = -1;
    private boolean dealtWith = false;
    
    public EditorMessage ()
    {
                
        super (OBJECT_TYPE);
                
    }
    
    public abstract String getMessageType ();
    public abstract boolean isEncrypted ();
    
    @Override
    public void fillToStringProperties (Map<String, Object> props)
    {
 
        super.fillToStringProperties (props);
 
        this.addToStringProperties (props,
                                    "messageType",
                                    this.getMessageType ());
        this.addToStringProperties (props,
                                    "encrypted",
                                    this.isEncrypted ());
        this.addToStringProperties (props,
                                    "messageId",
                                    this.messageId);
        this.addToStringProperties (props,
                                    "dealtWith",
                                    this.dealtWith);
        this.addToStringProperties (props,
                                    "sentByMe",
                                    this.sentByMe);
        this.addToStringProperties (props,
                                    "forProjectId",
                                    this.forProjectId);
        this.addToStringProperties (props,
                                    "when",
                                    this.when);
        this.addToStringProperties (props,
                                    "editor",
                                    (this.editor != null ? this.editor.getEmail () : null));
        this.addToStringProperties (props,
                                    "editorMessagingUsername",
                                    (this.editor != null ? this.editor.getMessagingUsername () : null));
        
    }    
    
    @Override
    public String toString ()
    {
        
        return Environment.formatObjectToStringProperties (this);
        
    }
    
    public boolean isDealtWith ()
    {
        
        return this.dealtWith;
        
    }
    
    public void setDealtWith (boolean v)
    {
        
        boolean oldV = this.dealtWith;
        
        this.dealtWith = v;
        
        this.firePropertyChangedEvent (EditorMessage.DEALT_WITH,
                                       oldV,
                                       v);
        
    }
    
    public Date getWhen ()
    {
        
        return this.when;
        
    }
    
    public void setWhen (Date d)
    {
        
        this.when = d;
        
    }
    
    public void setSentByMe (boolean v)
    {
        
        this.sentByMe = v;
        
    }
    
    public boolean isSentByMe ()
    {
        
        return this.sentByMe;
        
    }
        
    public abstract String getMessage ()
                                throws GeneralException;
    
    public abstract void setMessage (String m)
                              throws GeneralException;
    
    public void setOriginalMessage (String m)
    {
        
        this.origMessage = m;
        
    }
    
    public String getOriginalMessage ()
    {
        
        return this.origMessage;
        
    }
    
    public void setMessageId (String id)
    {
        
        if (this.messageId != null)
        {
            
            throw new IllegalStateException ("Already have a message id");
            
        }
        
        this.messageId = id;
        
    }
    
    public String getMessageId ()
    {
        
        return this.messageId;
        
    }
    
    public void setForProjectName (String n)
    {
        
        this.forProjectName = n;
        
    }
    
    /**
     * Since so many messages relate to projects we place this call here for convenience.
     * If there is a forProjectId value then it will be used to lookup the project name via:
     * {@link Environment.getProjectById(String)} to ensure we are using the current name.
     * If the project no longer exists then return the projectName specified by the message,
     * if that value isn't available then return "Unknown {Project}".
     *
     * If the forProjectId is not specified then null is returned.
     *
     * @return The project name.
     */
    public String getForProjectName ()
    {

        if (this.forProjectId == null)
        {
            
            return null;
            
        }
    
        String projName = this.forProjectName;

        try
        {
            
            Project proj = Environment.getProjectById (this.forProjectId,
                                                       null);
            
            if (proj != null)
            {
                
                projName = proj.getName ();
                
            }
            
        } catch (Exception e) {
            
            Environment.logError ("Unable to get project for id: " +
                                  this.forProjectId,
                                  e);
                        
        }
        
        if (projName == null)
        {
            
            projName = "Unknown {Project}";
            
        }
        
        return projName;
        
    }
    
    public String getForProjectId ()
    {
        
        return this.forProjectId;
        
    }
    
    public void setForProjectId (String id)
    {
        
        this.forProjectId = id;
        
    }
    
    public DataObject getObjectForReference (ObjectReference r)
    {
        
        return null;
        
    }
    
    public Map toMap ()
                      throws Exception
    {
        
        Map m = new HashMap ();
        
        this.fillMap (m);
        
        if (this.forProjectId != null)
        {
            
            m.put (MessageFieldNames.projectid,
                   this.forProjectId);
            
        }
        
        if (this.messageId == null)
        {
            
            this.messageId = EditorsEnvironment.getNewMessageId (this.editor,
                                                                 this.getMessageType ());

            m.put (MessageFieldNames.messageid,
                   this.messageId);
                                                             
        }

        if (this.wantsAck)
        {
            
            m.put (MessageFieldNames.requestack,
                   true);
                                    
        }

        m.put (MessageFieldNames.messagetype,
               this.getMessageType ());
                
        return m;
        
    }
    
    public void init (Map          data,
                      EditorEditor from)
               throws Exception
    {
        
        this.editor = from;
        this.sentByMe = false;
        
        this.forProjectId = this.getString (MessageFieldNames.projectid,
                                            data,
                                            false);

        this.messageId = this.getString (MessageFieldNames.messageid,
                                         data,
                                         false);
        
        this.wantsAck = this.getBoolean (MessageFieldNames.requestack,
                                         data,
                                         false);
        
        this.doInit (data,
                     from);
        
    }
    
    public void setEditor (EditorEditor ed)
    {
        
        if (ed == null)
        {
            
            throw new IllegalArgumentException ("No editor provided.");
            
        }
        
        this.editor = ed;
        
    }
    
    public EditorEditor getEditor ()
    {
        
        return this.editor;
        
    }
    
    protected abstract void fillMap (Map data)
                              throws Exception;
    
    protected abstract void doInit (Map          data,
                                    EditorEditor from)
                             throws Exception;

    protected String getString (String  field,
                                Map     data)
                         throws GeneralException
    {

        return this.getString (field,
                               data,
                               true);
    
    }
    
    protected String getString (String  field,
                                Map     data,
                                boolean required)
                         throws GeneralException
    {
        
        return TypeEncoder.getString (field,
                                      data,
                                      required);
        
    }
    
    protected int getInt (String  field,
                          Map     data)
                   throws GeneralException
    {

        return TypeEncoder.getInt (field,
                                   data,
                                   true);
    
    }
    
    protected int getInt (String  field,
                          Map     data,
                          boolean required)
                   throws GeneralException
    {
        
        return TypeEncoder.getInt (field,
                                   data,
                                   required);
        
    }

    protected Date getDate (String  field,
                            Map     data)
                     throws GeneralException
    {

        return TypeEncoder.getDate (field,
                                    data,
                                    true);
    
    }
    
    protected Date getDate (String  field,
                            Map     data,
                            boolean required)
                     throws GeneralException
    {
        
        return TypeEncoder.getDate (field,
                                    data,
                                    required);
        
    }

    protected boolean getBoolean (String  field,
                                  Map     data)
                           throws GeneralException
    {

        return TypeEncoder.getBoolean (field,
                                       data,
                                       true);
    
    }
    
    protected boolean getBoolean (String  field,
                                  Map     data,
                                  boolean required)
                           throws GeneralException
    {
        
        return TypeEncoder.getBoolean (field,
                                       data,
                                       required);
        
    }

    protected Object checkTypeAndNotNull (String field,
                                          Map    data,
                                          Class  expect)
                                   throws GeneralException
    {
        
        return TypeEncoder.checkTypeAndNotNull (field,
                                                data,
                                                expect);
        
    }
    
}