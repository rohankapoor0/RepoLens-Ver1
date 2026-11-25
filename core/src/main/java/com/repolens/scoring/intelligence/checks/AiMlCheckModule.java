package com.repolens.scoring.intelligence.checks;

import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.intelligence.ArchitectureObservation;
import com.repolens.scoring.intelligence.DetectedTechnology;
import com.repolens.scoring.intelligence.ImprovementPriority;
import com.repolens.scoring.intelligence.PositiveFinding;
import com.repolens.scoring.intelligence.ProjectImprovement;
import com.repolens.scoring.intelligence.ProjectType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Project-specific engineering intelligence for AI and Machine Learning repositories.
 */
public class AiMlCheckModule implements ProjectCheckModule {

    @Override
    public boolean appliesTo(Set<ProjectType> types, List<DetectedTechnology> technologies) {
        return types.contains(ProjectType.AI_MACHINE_LEARNING) || types.contains(ProjectType.DATA_SCIENCE);
    }

    @Override
    public void evaluate(
            RepoSnapshot snapshot,
            Set<ProjectType> types,
            List<DetectedTechnology> technologies,
            List<ArchitectureObservation> observations,
            List<PositiveFinding> positiveFindings,
            List<ProjectImprovement> recommendations
    ) {
        List<String> paths = snapshot.filePaths();
        String readme = snapshot.readmeText() != null ? snapshot.readmeText().toLowerCase(Locale.ROOT) : "";

        // 1. Dataset Documentation
        boolean hasDataDir = hasPath(paths, "data/") || hasPath(paths, "dataset/") || hasPath(paths, "datasets/");
        boolean hasDataDoc = readme.contains("dataset") || readme.contains("corpus") || readme.contains("training data");
        if (hasDataDir && hasDataDoc) {
            positiveFindings.add(new PositiveFinding(
                    "AI_DATASET_DOCS",
                    "Documented Dataset Pipeline",
                    "Dataset sources, schemas, and directories are clearly organized and documented.",
                    "Data Layer",
                    "Found data directory structure alongside dataset documentation in README."
            ));
        } else if (hasDataDir && !hasDataDoc) {
            recommendations.add(new ProjectImprovement(
                    "AI_DATASET_DOCS",
                    "Document Dataset Sources and Preprocessing Pipeline",
                    "Without dataset documentation, ML models cannot be reliably trained, reproduced, or verified for data leakage.",
                    "Detected data directory in tree, but README lacks dataset description, source citations, or preprocessing steps.",
                    "Add a '## Dataset' section in README.md detailing dataset origin, format, split ratios (train/val/test), and preprocessing steps.",
                    ImprovementPriority.HIGH,
                    20,
                    "README.md",
                    """
                    ## Dataset & Preprocessing
                    - **Source:** [Dataset Name / Link]
                    - **Format:** CSV / Parquet / Images
                    - **Splits:** 80% Train, 10% Validation, 10% Test
                    - **Preprocessing:** Run `python src/preprocess.py`
                    """.stripIndent()
            ));
        }

        // 2. Training vs Inference Separation
        boolean hasTrain = hasFile(paths, "train.py") || hasPath(paths, "training/") || hasPath(paths, "train/");
        boolean hasInference = hasFile(paths, "predict.py") || hasFile(paths, "infer.py") || hasPath(paths, "inference/")
                || hasPath(paths, "api/") || hasFile(paths, "serve.py");

        if (hasTrain && hasInference) {
            observations.add(new ArchitectureObservation(
                    "Decoupled Training & Inference",
                    "Training routines and inference serving scripts are cleanly separated into distinct modules.",
                    "ML Architecture",
                    "Detected dedicated training scripts alongside separate prediction/inference pipelines."
            ));
            positiveFindings.add(new PositiveFinding(
                    "AI_TRAIN_INFER_SEPARATION",
                    "Modular Training and Inference",
                    "Model training routines are separated from inference execution, facilitating independent deployment.",
                    "ML Architecture",
                    "Found distinct training and inference modules."
            ));
        } else if (hasTrain && !hasInference) {
            recommendations.add(new ProjectImprovement(
                    "AI_TRAIN_INFER_SEPARATION",
                    "Decouple Model Inference from Training Scripts",
                    "Bundling inference inside training scripts complicates deployment and prevents wrapping the model in an API service.",
                    "Detected training modules but no dedicated predict.py, infer.py, or inference serving module.",
                    "Extract model loading and prediction logic into an isolated predictor module or class.",
                    ImprovementPriority.MEDIUM,
                    30,
                    "src/predict.py",
                    """
                    class ModelPredictor:
                        def __init__(self, model_path: str):
                            self.model = load_model(model_path)

                        def predict(self, input_data):
                            return self.model.predict(input_data)
                    """.stripIndent()
            ));
        }

        // 3. Model Storage Conventions (Check for large weight blobs in Git)
        String committedWeightFile = null;
        long weightSize = 0;
        for (Map.Entry<String, Long> entry : snapshot.fileSizes().entrySet()) {
            String p = entry.getKey().toLowerCase(Locale.ROOT);
            if ((p.endsWith(".pt") || p.endsWith(".pth") || p.endsWith(".h5") || p.endsWith(".bin") || p.endsWith(".onnx") || p.endsWith(".pkl"))
                    && entry.getValue() != null && entry.getValue() > 5 * 1024 * 1024L) {
                committedWeightFile = entry.getKey();
                weightSize = entry.getValue();
                break;
            }
        }

        if (committedWeightFile != null) {
            long mb = weightSize / (1024 * 1024);
            recommendations.add(new ProjectImprovement(
                    "AI_MODEL_WEIGHT_STORAGE",
                    "Migrate Large Model Weights to Git LFS or Artifact Registry",
                    "Committing large model weight binaries directly to git repo permanently inflates clone sizes and slows developer workflows.",
                    "Detected large model artifact (" + committedWeightFile + ", ~" + mb + " MB) committed directly to git.",
                    "Track large model weights with Git LFS (`git lfs track \"*.pt\"`) or download weights dynamically from Hugging Face Hub / S3 upon initialization.",
                    ImprovementPriority.HIGH,
                    20,
                    committedWeightFile,
                    "git lfs install\ngit lfs track \"*.pt\"\ngit lfs track \"*.onnx\""
            ));
        }

        // 4. Notebook Organization
        long rootNotebooks = paths.stream()
                .filter(p -> p.endsWith(".ipynb") && !p.contains("/"))
                .count();
        long subNotebooks = paths.stream()
                .filter(p -> p.endsWith(".ipynb") && p.contains("/"))
                .count();

        if (subNotebooks > 0 && rootNotebooks == 0) {
            positiveFindings.add(new PositiveFinding(
                    "AI_NOTEBOOK_ORGANIZATION",
                    "Clean Notebook Structuring",
                    "Jupyter notebooks are properly isolated in dedicated directories rather than cluttered at repository root.",
                    "Repository Layout",
                    "All detected *.ipynb notebooks reside within organized subdirectories."
            ));
        } else if (rootNotebooks > 1) {
            recommendations.add(new ProjectImprovement(
                    "AI_NOTEBOOK_ORGANIZATION",
                    "Organize Exploratory Notebooks into a Dedicated Directory",
                    "Leaving multiple exploratory notebooks at the root makes the project harder to navigate and maintain.",
                    "Found " + rootNotebooks + " Jupyter notebooks (.ipynb) located directly at repository root.",
                    "Move interactive notebooks to `notebooks/` and prefix them with execution sequence numbers (e.g. `01_eda.ipynb`, `02_train.ipynb`).",
                    ImprovementPriority.LOW,
                    15,
                    "notebooks/",
                    "mkdir -p notebooks\nmv *.ipynb notebooks/"
            ));
        }

        // 5. Model Evaluation Metrics in README
        boolean hasMetrics = readme.contains("accuracy") || readme.contains("f1") || readme.contains("precision")
                || readme.contains("recall") || readme.contains("loss") || readme.contains("benchmark")
                || readme.contains("bleu") || readme.contains("rouge");
        if (hasMetrics) {
            positiveFindings.add(new PositiveFinding(
                    "AI_EVAL_METRICS",
                    "Documented Model Evaluation Metrics",
                    "Model evaluation criteria, benchmarks, or performance metrics are transparently documented.",
                    "Model Quality",
                    "README includes documented performance metrics or benchmark results."
            ));
        } else if (types.contains(ProjectType.AI_MACHINE_LEARNING)) {
            recommendations.add(new ProjectImprovement(
                    "AI_EVAL_METRICS",
                    "Document Model Evaluation Methodology and Benchmark Results",
                    "Engineers and recruiters evaluating your ML project need to know how the model was validated and its empirical performance.",
                    "No evaluation metrics (Accuracy, F1 score, Loss, Inference latency) detected in documentation.",
                    "Add a '## Evaluation & Results' section with validation metrics, confusion matrix, or comparison against baselines.",
                    ImprovementPriority.MEDIUM,
                    20,
                    "README.md",
                    """
                    ## Evaluation & Results
                    | Metric | Score |
                    |---|---|
                    | Accuracy | 94.2% |
                    | F1-Score | 0.93 |
                    | Inference Latency | 24ms |
                    """.stripIndent()
            ));
        }
    }

    private static boolean hasFile(List<String> paths, String target) {
        for (String p : paths) {
            int idx = p.lastIndexOf('/');
            String filename = idx >= 0 ? p.substring(idx + 1) : p;
            if (filename.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private static boolean hasPath(List<String> paths, String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        for (String p : paths) {
            if (p.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }
}
