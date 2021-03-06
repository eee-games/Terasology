/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.engine.bootstrap;

import org.terasology.context.Context;
import org.terasology.engine.SimpleUri;
import org.terasology.engine.module.ModuleManager;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.internal.EngineEntityManager;
import org.terasology.entitySystem.entity.internal.PojoEntityManager;
import org.terasology.entitySystem.event.Event;
import org.terasology.entitySystem.event.internal.EventSystem;
import org.terasology.entitySystem.event.internal.EventSystemImpl;
import org.terasology.entitySystem.metadata.ComponentLibrary;
import org.terasology.entitySystem.metadata.EntitySystemLibrary;
import org.terasology.entitySystem.metadata.EventLibrary;
import org.terasology.entitySystem.metadata.MetadataUtil;
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.entitySystem.prefab.internal.PojoPrefabManager;
import org.terasology.entitySystem.systems.internal.DoNotAutoRegister;
import org.terasology.logic.behavior.asset.NodesClassLibrary;
import org.terasology.module.ModuleEnvironment;
import org.terasology.network.NetworkSystem;
import org.terasology.persistence.typeHandling.TypeSerializationLibrary;
import org.terasology.reflection.copy.CopyStrategyLibrary;
import org.terasology.reflection.reflect.ReflectFactory;
import org.terasology.reflection.reflect.ReflectionReflectFactory;
import org.terasology.rendering.nui.properties.OneOfProviderFactory;

/**
 * Provides static methods that can be used to put entity system related objects into a {@link Context} instance.
 */
public class EntitySystemSetupUtil {


    private EntitySystemSetupUtil() {
        // static utility class, no instance needed
    }

    public static void addReflectionBasedLibraries(Context context) {
        ReflectionReflectFactory reflectFactory = new ReflectionReflectFactory();
        context.put(ReflectFactory.class, reflectFactory);
        context.put(CopyStrategyLibrary.class, new CopyStrategyLibrary(reflectFactory));
    }


    /**
     * Objects for the following classes must be available in the context:
     * <ul>
     *     <li>{@link ModuleEnvironment}</li>
     *     <li>{@link NetworkSystem}</li>
     *     <li>{@link ReflectFactory}</li>
     *     <li>{@link CopyStrategyLibrary}</li>
     * </ul>
     *
     * The method will make objects for the following classes available in the context:
     * <ul>
     *     <li>{@link EngineEntityManager}</li>
     *     <li>{@link ComponentLibrary}</li>
     *     <li>{@link EventLibrary}</li>
     *     <li>{@link PrefabManager}</li>
     *     <li>{@link EventSystem}</li>
     *     <li>{@link NodesClassLibrary}</li>
     * </ul>
     *
     */
    public static void addEntityManagementRelatedClasses(Context context) {
        ModuleEnvironment environment = context.get(ModuleManager.class).getEnvironment();
        NetworkSystem networkSystem = context.get(NetworkSystem.class);
        ReflectFactory reflectFactory = context.get(ReflectFactory.class);
        CopyStrategyLibrary copyStrategyLibrary = context.get(CopyStrategyLibrary.class);

        // Entity Manager
        PojoEntityManager entityManager = new PojoEntityManager();
        context.put(EntityManager.class, entityManager);
        context.put(EngineEntityManager.class, entityManager);

        // Standard serialization library
        TypeSerializationLibrary typeSerializationLibrary = TypeSerializationLibrary.createDefaultLibrary(entityManager,
                reflectFactory, copyStrategyLibrary);
        entityManager.setTypeSerializerLibrary(typeSerializationLibrary);

        // Entity System Library
        EntitySystemLibrary library = new EntitySystemLibrary(context, typeSerializationLibrary);
        context.put(EntitySystemLibrary.class, library);
        entityManager.setComponentLibrary(library.getComponentLibrary());
        context.put(ComponentLibrary.class, library.getComponentLibrary());
        context.put(EventLibrary.class, library.getEventLibrary());

        // Prefab Manager
        PrefabManager prefabManager = new PojoPrefabManager();
        entityManager.setPrefabManager(prefabManager);
        context.put(PrefabManager.class, prefabManager);

        // Event System
        EventSystem eventSystem = new EventSystemImpl(library.getEventLibrary(), networkSystem);
        entityManager.setEventSystem(eventSystem);
        context.put(EventSystem.class, eventSystem);

        // TODO: Review - NodeClassLibrary related to the UI for behaviours. Should not be here and probably not even in the CoreRegistry
        context.put(OneOfProviderFactory.class, new OneOfProviderFactory());

        // Behaviour Trees Node Library
        NodesClassLibrary nodesClassLibrary = new NodesClassLibrary(context);
        context.put(NodesClassLibrary.class, nodesClassLibrary);
        nodesClassLibrary.scan(environment);

        registerComponents(library.getComponentLibrary(), environment);
        registerEvents(entityManager.getEventSystem(), environment);
    }

    private static void registerComponents(ComponentLibrary library, ModuleEnvironment environment) {
        for (Class<? extends Component> componentType : environment.getSubtypesOf(Component.class)) {
            if (componentType.getAnnotation(DoNotAutoRegister.class) == null) {
                String componentName = MetadataUtil.getComponentClassName(componentType);
                library.register(new SimpleUri(environment.getModuleProviding(componentType), componentName), componentType);
            }
        }
    }

    private static void registerEvents(EventSystem eventSystem, ModuleEnvironment environment) {
        for (Class<? extends Event> type : environment.getSubtypesOf(Event.class)) {
            if (type.getAnnotation(DoNotAutoRegister.class) == null) {
                eventSystem.registerEvent(new SimpleUri(environment.getModuleProviding(type), type.getSimpleName()), type);
            }
        }
    }

}
