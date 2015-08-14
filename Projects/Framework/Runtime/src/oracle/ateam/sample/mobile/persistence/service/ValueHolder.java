/*******************************************************************************
 Copyright (c) 2014,2015, Oracle and/or its affiliates. All rights reserved.
 
 $revision_history$
 14-aug-2015   Steven Davelaar
 1.1           delegate check for offline to service class
 06-feb-2013   Steven Davelaar
 1.0           initial creation
******************************************************************************/
package oracle.ateam.sample.mobile.persistence.service;

import java.util.Iterator;
import java.util.List;

import java.util.Map;

import oracle.adf.model.datacontrols.device.DeviceManagerFactory;

import oracle.adfmf.bindings.DataControl;
import oracle.adfmf.bindings.dbf.AmxBindingContext;
import oracle.adfmf.framework.api.AdfmfJavaUtilities;
import oracle.adfmf.framework.exception.AdfException;

import oracle.ateam.sample.mobile.persistence.manager.DBPersistenceManager;
import oracle.ateam.sample.mobile.persistence.metadata.AttributeMappingOneToOne;
import oracle.ateam.sample.mobile.persistence.metadata.ClassMappingDescriptor;
import oracle.ateam.sample.mobile.persistence.model.Entity;
import oracle.ateam.sample.mobile.persistence.util.EntityUtils;
import oracle.ateam.sample.mobile.util.ADFMobileLogger;

/**
 * Class that implements lazy loading (aka as Indirection) of 1:1 relations in a data object.
 * For example, the Department object can have a getManager method which uses a ValueHolder to return 
 * the employee on the first call of the getManager method. 
 * This class will execute a SQL statement to retrieve the manager from the database and populate the list. 

 */
public class ValueHolder
  implements ValueHolderInterface
{
  private static ADFMobileLogger sLog = ADFMobileLogger.createLogger(ValueHolder.class);
  private transient Object value;
  private AttributeMappingOneToOne mapping;
  private Entity entity;
  private boolean initialized = false;

  public ValueHolder(Entity entity, AttributeMappingOneToOne mapping)
  {
    this.mapping = mapping;
    this.entity = entity;
  }

  public Object getValue()
  {
    if (mapping!=null && !isInitialized() && !entity.getIsNewEntity())
    {
      value = getReferenceEntity();
      setInitialized(true);
    }
    return value;
  }

  public void setValue(Object value)
  {
    this.value = value;
  }

  protected Entity getReferenceEntity()
  {
    ClassMappingDescriptor referenceDescriptor = mapping.getReferenceClassMappingDescriptor();
    DBPersistenceManager pm = EntityUtils.getLocalPersistenceManager(referenceDescriptor);
    EntityCRUDService service = EntityUtils.getEntityCRUDService(referenceDescriptor);  
    boolean offline = service.isOffline();
    String accessorAttribute = mapping.getAttributeName();
    if (mapping.getAccessorMethod() != null && !offline)
    {
      sLog.fine("Getter method for attribute " + this.mapping.getAttributeName() +
                " called for the first time, calling get-as-parent web service method");
      if (service.getRemotePersistenceManager() == null)
      {
        sLog.fine("Cannot execute GetAsParent, no remote persistence manager configured");
        return null;
      }
      else if (!service.getRemotePersistenceManager().isGetAsParentSupported(service.getEntityClass(),accessorAttribute))
      {
        sLog.fine("Cannot execute GetAsParent, no GetAsParent method defined for entity "+service.getEntityClass().getName());
        return null;
      }  
      Entity parent =  service.getRemotePersistenceManager().getAsParent(service.getEntityClass(), entity, accessorAttribute);
      if (parent!=null)
      {
        // update foreign key column(s) in child entity so we can restore the relationship in ofline mode
        Map columnMappings  = mapping.getColumnMappings();
        Iterator iterator = columnMappings.keySet().iterator();
        ClassMappingDescriptor childDescriptor = mapping.getClassMappingDescriptor();
        while (iterator.hasNext())
        {
          String childColumn = (String) iterator.next();
          String childAttribute = childDescriptor.findAttributeMappingByColumnName(childColumn).getAttributeName();
          String parentColumn = (String) columnMappings.get(childColumn);
          String parentAttribute = referenceDescriptor.findAttributeMappingByColumnName(parentColumn).getAttributeName();
          entity.setAttributeValue(childAttribute, parent.getAttributeValue(parentAttribute));
        }        
        pm.mergeEntity(entity, true);
      }
      return parent;
    }
    else
    {
      sLog.fine("Getter method for attribute "+mapping.getAttributeName()+" called for the first time, querying database to retrieve the value");
      Class referenceClazz = referenceDescriptor.getClazz();
      Entity referenced = pm.getAsParent(referenceClazz, entity, mapping);
      return referenced;
    }
  }

  public void setInitialized(boolean initialized)
  {
    this.initialized = initialized;
  }

  public boolean isInitialized()
  {
    return initialized;
  }


}
