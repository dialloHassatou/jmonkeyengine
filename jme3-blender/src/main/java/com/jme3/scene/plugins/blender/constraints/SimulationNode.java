package com.jme3.scene.plugins.blender.constraints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.Animation;
import com.jme3.animation.Bone;
import com.jme3.animation.BoneTrack;
import com.jme3.animation.Skeleton;
import com.jme3.animation.SpatialTrack;
import com.jme3.animation.Track;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.blender.BlenderContext;
import com.jme3.scene.plugins.blender.BlenderContext.LoadedDataType;
import com.jme3.scene.plugins.blender.animations.BoneContext;
import com.jme3.scene.plugins.blender.objects.ObjectHelper;
import com.jme3.util.TempVars;

/**
 * A node that represents either spatial or bone in constraint simulation. The
 * node is applied its translation, rotation and scale for each frame of its
 * animation. Then the constraints are applied that will eventually alter it.
 * After that the feature's transformation is stored in VirtualTrack which is
 * converted to new bone or spatial track at the very end.
 * 
 * @author Marcin Roguski (Kaelthas)
 */
public class SimulationNode {
    private static final Logger  LOGGER   = Logger.getLogger(SimulationNode.class.getName());

    /** The blender context. */
    private BlenderContext       blenderContext;
    /** The name of the node (for debugging purposes). */
    private String               name;
    /** A list of children for the node (either bones or child spatials). */
    private List<SimulationNode> children = new ArrayList<SimulationNode>();
    /** A list of constraints that the current node has. */
    private List<Constraint>     constraints;
    /** A list of node's animations. */
    private List<Animation>      animations;

    /** The nodes spatial (if null then the boneContext should be set). */
    private Spatial              spatial;
    /** The skeleton of the bone (not null if the node simulated the bone). */
    private Skeleton             skeleton;
    /** Animation controller for the node's feature. */
    private AnimControl          animControl;

    /**
     * The star transform of a spatial. Needed to properly reset the spatial to
     * its start position.
     */
    private Transform            spatialStartTransform;
    /** Star transformations for bones. Needed to properly reset the bones. */
    private Map<Bone, Transform> boneStartTransforms;

    /**
     * Builds the nodes tree for the given feature. The feature (bone or
     * spatial) is found by its OMA. The feature must be a root bone or a root
     * spatial.
     * 
     * @param featureOMA
     *            the OMA of either bone or spatial
     * @param blenderContext
     *            the blender context
     */
    public SimulationNode(Long featureOMA, BlenderContext blenderContext) {
        this(featureOMA, blenderContext, true);
    }

    /**
     * Creates the node for the feature.
     * 
     * @param featureOMA
     *            the OMA of either bone or spatial
     * @param blenderContext
     *            the blender context
     * @param rootNode
     *            indicates if the feature is a root bone or root spatial or not
     */
    private SimulationNode(Long featureOMA, BlenderContext blenderContext, boolean rootNode) {
        this.blenderContext = blenderContext;
        Node spatial = (Node) blenderContext.getLoadedFeature(featureOMA, LoadedDataType.FEATURE);
        if (blenderContext.getMarkerValue(ObjectHelper.ARMATURE_NODE_MARKER, spatial) != null) {
            skeleton = blenderContext.getSkeleton(featureOMA);

            Node nodeWithAnimationControl = blenderContext.getControlledNode(skeleton);
            animControl = nodeWithAnimationControl.getControl(AnimControl.class);

            boneStartTransforms = new HashMap<Bone, Transform>();
            for (int i = 0; i < skeleton.getBoneCount(); ++i) {
                Bone bone = skeleton.getBone(i);
                boneStartTransforms.put(bone, new Transform(bone.getBindPosition(), bone.getBindRotation(), bone.getBindScale()));
            }
        } else {
            if (rootNode && spatial.getParent() != null) {
                throw new IllegalStateException("Given spatial must be a root node!");
            }
            this.spatial = spatial;
            spatialStartTransform = spatial.getLocalTransform().clone();
        }

        name = '>' + spatial.getName() + '<';

        constraints = this.findConstraints(featureOMA, blenderContext);
        if (constraints == null) {
            constraints = new ArrayList<Constraint>();
        }

        // add children nodes
        if (skeleton != null) {
            // bone with index 0 is a root bone and should not be considered
            // here
            for (int i = 1; i < skeleton.getBoneCount(); ++i) {
                BoneContext boneContext = blenderContext.getBoneContext(skeleton.getBone(i));
                List<Constraint> boneConstraints = this.findConstraints(boneContext.getBoneOma(), blenderContext);
                if (boneConstraints != null) {
                    constraints.addAll(boneConstraints);
                }
            }
            Node node = blenderContext.getControlledNode(skeleton);
            Long animatedNodeOMA = ((Number) blenderContext.getMarkerValue(ObjectHelper.OMA_MARKER, node)).longValue();
            animations = blenderContext.getAnimations(animatedNodeOMA);
        } else {
            animations = blenderContext.getAnimations(featureOMA);
            for (Spatial child : spatial.getChildren()) {
                if (child instanceof Node) {
                    children.add(new SimulationNode((Long) blenderContext.getMarkerValue(ObjectHelper.OMA_MARKER, child), blenderContext, false));
                }
            }
        }

        LOGGER.info("Removing invalid constraints.");
        List<Constraint> validConstraints = new ArrayList<Constraint>(constraints.size());
        for (Constraint constraint : constraints) {
            if (constraint.validate()) {
                validConstraints.add(constraint);
            } else {
                LOGGER.log(Level.WARNING, "Constraint {0} is invalid and will not be applied.", constraint.name);
            }
        }
        constraints = validConstraints;
    }

    /**
     * Tells if the node already contains the given constraint (so that it is
     * not applied twice).
     * 
     * @param constraint
     *            the constraint to be checked
     * @return <b>true</b> if the constraint already is stored in the node and
     *         <b>false</b> otherwise
     */
    public boolean contains(Constraint constraint) {
        boolean result = false;
        if (constraints != null && constraints.size() > 0) {
            for (Constraint c : constraints) {
                if (c.equals(constraint)) {
                    return true;
                }
            }
        }
        return result;
    }

    /**
     * Resets the node's feature to its starting transformation.
     */
    private void reset() {
        if (spatial != null) {
            spatial.setLocalTransform(spatialStartTransform);
            for (SimulationNode child : children) {
                child.reset();
            }
        } else if (skeleton != null) {
            for (Entry<Bone, Transform> entry : boneStartTransforms.entrySet()) {
                Transform t = entry.getValue();
                entry.getKey().setBindTransforms(t.getTranslation(), t.getRotation(), t.getScale());
            }
            skeleton.reset();
        }
    }

    /**
     * Simulates the spatial node.
     */
    private void simulateSpatial() {
        if (constraints != null && constraints.size() > 0) {
            boolean applyStaticConstraints = true;
            if (animations != null) {
                for (Animation animation : animations) {
                    float[] animationTimeBoundaries = this.computeAnimationTimeBoundaries(animation);
                    int maxFrame = (int) animationTimeBoundaries[0];
                    float maxTime = animationTimeBoundaries[1];

                    VirtualTrack vTrack = new VirtualTrack(spatial.getName(), maxFrame, maxTime);
                    for (Track track : animation.getTracks()) {
                        for (int frame = 0; frame < maxFrame; ++frame) {
                            spatial.setLocalTranslation(((SpatialTrack) track).getTranslations()[frame]);
                            spatial.setLocalRotation(((SpatialTrack) track).getRotations()[frame]);
                            spatial.setLocalScale(((SpatialTrack) track).getScales()[frame]);

                            for (Constraint constraint : constraints) {
                                constraint.apply(frame);
                                vTrack.setTransform(frame, spatial.getLocalTransform());
                            }
                        }
                        Track newTrack = vTrack.getAsSpatialTrack();
                        if (newTrack != null) {
                            animation.removeTrack(track);
                            animation.addTrack(newTrack);
                        }
                        applyStaticConstraints = false;
                    }
                }
            }

            // if there are no animations then just constraint the static
            // object's transformation
            if (applyStaticConstraints) {
                for (Constraint constraint : constraints) {
                    constraint.apply(0);
                }
            }
        }

        for (SimulationNode child : children) {
            child.simulate();
        }
    }

    /**
     * Simulates the bone node.
     */
    private void simulateSkeleton() {
        if (constraints != null && constraints.size() > 0) {
            Set<Long> alteredOmas = new HashSet<Long>();

            if (animations != null) {
                TempVars vars = TempVars.get();
                AnimChannel animChannel = animControl.createChannel();

                List<Bone> bonesWithConstraints = this.collectBonesWithConstraints(skeleton);
                for (Animation animation : animations) {
                    float[] animationTimeBoundaries = this.computeAnimationTimeBoundaries(animation);
                    int maxFrame = (int) animationTimeBoundaries[0];
                    float maxTime = animationTimeBoundaries[1];

                    Map<Integer, VirtualTrack> tracks = new HashMap<Integer, VirtualTrack>();
                    for (int frame = 0; frame < maxFrame; ++frame) {
                        // this MUST be done here, otherwise setting next frame of animation will
                        // lead to possible errors
                        this.reset();

                        // first set proper time for all bones in all the tracks ...
                        for (Track track : animation.getTracks()) {
                            float time = ((BoneTrack) track).getTimes()[frame];
                            track.setTime(time, 1, animControl, animChannel, vars);
                            skeleton.updateWorldVectors();
                        }

                        // ... and then apply constraints from the root bone to the last child ...
                        for (Bone rootBone : bonesWithConstraints) {
                            this.applyConstraints(rootBone, alteredOmas, frame);
                        }

                        // ... add virtual tracks if neccessary, for bones that were altered but had no tracks before ...
                        for (Long boneOMA : alteredOmas) {
                            BoneContext boneContext = blenderContext.getBoneContext(boneOMA);
                            int boneIndex = skeleton.getBoneIndex(boneContext.getBone());
                            if (!tracks.containsKey(boneIndex)) {
                                tracks.put(boneIndex, new VirtualTrack(boneContext.getBone().getName(), maxFrame, maxTime));
                            }
                        }
                        alteredOmas.clear();

                        // ... and fill in another frame in the result track
                        for (Entry<Integer, VirtualTrack> trackEntry : tracks.entrySet()) {
                            Bone bone = skeleton.getBone(trackEntry.getKey());
                            Transform startTransform = boneStartTransforms.get(bone);

                            // track contains differences between the frame position and bind positions of bones/spatials
                            Vector3f bonePositionDifference = bone.getLocalPosition().subtract(startTransform.getTranslation());
                            Quaternion boneRotationDifference = startTransform.getRotation().inverse().mult(bone.getLocalRotation()).normalizeLocal();
                            Vector3f boneScaleDifference = bone.getLocalScale().divide(startTransform.getScale());

                            trackEntry.getValue().setTransform(frame, new Transform(bonePositionDifference, boneRotationDifference, boneScaleDifference));
                        }
                    }

                    for (Entry<Integer, VirtualTrack> trackEntry : tracks.entrySet()) {
                        Track newTrack = trackEntry.getValue().getAsBoneTrack(trackEntry.getKey());
                        if (newTrack != null) {
                            boolean trackReplaced = false;
                            for (Track track : animation.getTracks()) {
                                if (((BoneTrack) track).getTargetBoneIndex() == trackEntry.getKey().intValue()) {
                                    animation.removeTrack(track);
                                    animation.addTrack(newTrack);
                                    trackReplaced = true;
                                    break;
                                }
                            }
                            if (!trackReplaced) {
                                animation.addTrack(newTrack);
                            }
                        }
                    }
                }
                vars.release();
                animControl.clearChannels();
                this.reset();
            }
        }
    }

    /**
     * Applies constraints to the given bone and its children.
     * The goal is to apply constraint from root bone to the last child.
     * @param bone
     *            the bone whose constraints will be applied
     * @param alteredOmas
     *            the set of OMAS of the altered bones (is populated if necessary)
     * @param frame
     *            the current frame of the animation
     */
    private void applyConstraints(Bone bone, Set<Long> alteredOmas, int frame) {
        BoneContext boneContext = blenderContext.getBoneContext(bone);
        List<Constraint> constraints = this.findConstraints(boneContext.getBoneOma(), blenderContext);
        if (constraints != null && constraints.size() > 0) {
            for (Constraint constraint : constraints) {
                constraint.apply(frame);
                if (constraint.getAlteredOmas() != null) {
                    alteredOmas.addAll(constraint.getAlteredOmas());
                }
                alteredOmas.add(boneContext.getBoneOma());
            }
        }
    }

    /**
     * Simulates the node.
     */
    public void simulate() {
        this.reset();
        if (spatial != null) {
            this.simulateSpatial();
        } else {
            this.simulateSkeleton();
        }
    }

    /**
     * Collects the bones that will take part in constraint computations.
     * The result will not include bones whose constraints will not change them or are invalid.
     * The bones are sorted so that the constraint applying is done in the proper order.
     * @param skeleton
     *            the simulated skeleton
     * @return a list of bones that will take part in constraints computations
     */
    private List<Bone> collectBonesWithConstraints(Skeleton skeleton) {
        Map<BoneContext, List<Constraint>> bonesWithConstraints = new HashMap<BoneContext, List<Constraint>>();
        for (int i = 1; i < skeleton.getBoneCount(); ++i) {// ommit the 0 - indexed root bone as it is the bone added by importer
            Bone bone = skeleton.getBone(i);
            BoneContext boneContext = blenderContext.getBoneContext(bone);
            List<Constraint> constraints = this.findConstraints(boneContext.getBoneOma(), blenderContext);
            if (constraints != null && constraints.size() > 0) {
                bonesWithConstraints.put(boneContext, constraints);
            }
        }

        // first sort out constraints that are not implemented or invalid or will not affect the bone's tracks
        List<BoneContext> bonesToRemove = new ArrayList<BoneContext>(bonesWithConstraints.size());
        for (Entry<BoneContext, List<Constraint>> entry : bonesWithConstraints.entrySet()) {
            List<Constraint> validConstraints = new ArrayList<Constraint>(entry.getValue().size());
            for (Constraint constraint : entry.getValue()) {// TODO: sprawdzi� czy wprowadza jakiekolwiek zmiany
                if (constraint.isImplemented() && constraint.validate() && constraint.isTrackToBeChanged()) {
                    validConstraints.add(constraint);
                }
            }
            if (validConstraints.size() > 0) {
                entry.setValue(validConstraints);
            } else {
                bonesToRemove.add(entry.getKey());
            }
        }
        for (BoneContext boneContext : bonesToRemove) {
            bonesWithConstraints.remove(boneContext);
        }

        List<BoneContext> bonesConstrainedWithoutTarget = new ArrayList<BoneContext>();
        Set<Long> remainedOMAS = new HashSet<Long>();
        // later move all bones with not dependant constraints to the front
        bonesToRemove.clear();
        for (Entry<BoneContext, List<Constraint>> entry : bonesWithConstraints.entrySet()) {
            boolean hasDependantConstraints = false;
            for (Constraint constraint : entry.getValue()) {
                if (constraint.targetOMA != null) {
                    hasDependantConstraints = true;
                    break;
                }
            }

            if (!hasDependantConstraints) {
                bonesConstrainedWithoutTarget.add(entry.getKey());
                bonesToRemove.add(entry.getKey());
            } else {
                remainedOMAS.add(entry.getKey().getBoneOma());
            }
        }
        for (BoneContext boneContext : bonesToRemove) {
            bonesWithConstraints.remove(boneContext);
        }

        this.sortBonesByChain(bonesConstrainedWithoutTarget);

        // another step is to add those bones whose constraints depend only on bones already added to the result or to those
        // that are not included neither in the result nor in the remaining map
        // do this as long as bones are being moved to the result and the 'bonesWithConstraints' is not empty
        List<BoneContext> bonesConstrainedWithTarget = new ArrayList<BoneContext>();
        do {
            bonesToRemove.clear();
            for (Entry<BoneContext, List<Constraint>> entry : bonesWithConstraints.entrySet()) {
                boolean unconstrainedBone = true;
                for (Constraint constraint : entry.getValue()) {
                    if (remainedOMAS.contains(constraint.getTargetOMA())) {
                        unconstrainedBone = false;
                        break;
                    }
                }
                if (unconstrainedBone) {
                    bonesToRemove.add(entry.getKey());
                    bonesConstrainedWithTarget.add(entry.getKey());
                }
            }

            for (BoneContext boneContext : bonesToRemove) {
                bonesWithConstraints.remove(boneContext);
                remainedOMAS.remove(boneContext.getBoneOma());
            }
        } while (bonesWithConstraints.size() > 0 && bonesToRemove.size() > 0);
        this.sortBonesByChain(bonesConstrainedWithoutTarget);

        // prepare the result
        List<Bone> result = new ArrayList<Bone>();
        for (BoneContext boneContext : bonesConstrainedWithoutTarget) {
            result.add(boneContext.getBone());
        }
        for (BoneContext boneContext : bonesConstrainedWithTarget) {
            result.add(boneContext.getBone());
        }

        // in the end prepare the mapping between bone OMA
        if (bonesWithConstraints.size() > 0) {
            LOGGER.warning("Some bones have loops in their constraints' definitions. The result might not be properly computed!");
            for (BoneContext boneContext : bonesWithConstraints.keySet()) {
                result.add(boneContext.getBone());
            }
        }

        return result;
    }

    /**
     * The method sorts the given bones from root to top.
     * If the list contains bones from different branches then those branches will be listed
     * one after another - which means that bones will be grouped by branches they belong to.
     * @param bones
     *            a list of bones
     */
    private void sortBonesByChain(List<BoneContext> bones) {
        Map<BoneContext, List<BoneContext>> branches = new HashMap<BoneContext, List<BoneContext>>();

        for (BoneContext bone : bones) {
            BoneContext root = bone.getRoot();
            List<BoneContext> list = branches.get(root);
            if (list == null) {
                list = new ArrayList<BoneContext>();
                branches.put(root, list);
            }
            list.add(bone);
        }

        // sort the bones in each branch from root to leaf
        bones.clear();
        for (Entry<BoneContext, List<BoneContext>> entry : branches.entrySet()) {
            Collections.sort(entry.getValue(), new Comparator<BoneContext>() {
                @Override
                public int compare(BoneContext o1, BoneContext o2) {
                    return o1.getDistanceFromRoot() - o2.getDistanceFromRoot();
                }
            });
            bones.addAll(entry.getValue());
        }
    }

    /**
     * Computes the maximum frame and time for the animation. Different tracks
     * can have different lengths so here the maximum one is being found.
     * 
     * @param animation
     *            the animation
     * @return maximum frame and time of the animation
     */
    private float[] computeAnimationTimeBoundaries(Animation animation) {
        int maxFrame = Integer.MIN_VALUE;
        float maxTime = -Float.MAX_VALUE;
        for (Track track : animation.getTracks()) {
            if (track instanceof BoneTrack) {
                maxFrame = Math.max(maxFrame, ((BoneTrack) track).getTranslations().length);
                maxTime = Math.max(maxTime, ((BoneTrack) track).getTimes()[((BoneTrack) track).getTimes().length - 1]);
            } else if (track instanceof SpatialTrack) {
                maxFrame = Math.max(maxFrame, ((SpatialTrack) track).getTranslations().length);
                maxTime = Math.max(maxTime, ((SpatialTrack) track).getTimes()[((SpatialTrack) track).getTimes().length - 1]);
            } else {
                throw new IllegalStateException("Unsupported track type for simuation: " + track);
            }
        }
        return new float[] { maxFrame, maxTime };
    }

    /**
     * Finds constraints for the node's features.
     * 
     * @param ownerOMA
     *            the feature's OMA
     * @param blenderContext
     *            the blender context
     * @return a list of feature's constraints or empty list if none were found
     */
    private List<Constraint> findConstraints(Long ownerOMA, BlenderContext blenderContext) {
        List<Constraint> result = new ArrayList<Constraint>();
        List<Constraint> constraints = blenderContext.getConstraints(ownerOMA);
        if (constraints != null) {
            for (Constraint constraint : constraints) {
                if (constraint.isImplemented() && constraint.validate()) {
                    result.add(constraint);
                } else {
                    LOGGER.log(Level.WARNING, "Constraint named: ''{0}'' of type ''{1}'' is not implemented and will NOT be applied!", new Object[] { constraint.name, constraint.getConstraintTypeName() });
                }
            }
        }
        return result.size() > 0 ? result : null;
    }

    @Override
    public String toString() {
        return name;
    }
}