package client.utils;

public class HillClimbing{
    double pi = 3.141592653589793;
    int m_wavePeriod;
    int m_samplesToMeasure;
    double m_targetThroughputRatio;
    double m_targetSignalToNoiseRatio;
    double m_maxChangePerSecond;
    double m_maxChangePerSample;
    int m_maxThreadWaveMagnitude;
    double m_threadMagnitudeMultiplier;
    double m_throughputErrorSmoothingFactor;
    double m_gainExponent;
    double m_maxSampleError;

    double m_currentControlSetting;
    int m_totalSamples;
    int m_lastThreadCount;
    double m_elapsedSinceLastChange; //elapsed seconds since last thread count change
    double m_completionsSinceLastChange; //number of completions since last thread count change

    double m_averageThroughputNoise;

    double[] m_samples;
    double[] m_threadCounts;

    int m_currentSampleInterval;
    int m_accumulatedCompletionCount;
    double m_accumulatedSampleDuration;

    enum HillClimbingStateTransition{
        Warmup,
        Initializing,
        RandomMove,
        ClimbingMove,
        ChangePoint,
        Stabilizing,
        Starvation, //used by ThreadpoolMgr
        ThreadTimedOut, //used by ThreadpoolMgr
        Undefined,
    };

    public HillClimbing(){
        m_wavePeriod = 2;
        m_maxThreadWaveMagnitude = 20;

        m_threadMagnitudeMultiplier = 100 / 100.0;
        m_samplesToMeasure = m_wavePeriod * 4;
        m_targetThroughputRatio = 15 / 100.0;
        m_targetSignalToNoiseRatio = 300 / 100.0;
        m_maxChangePerSecond = 4;
        m_maxChangePerSample =  20;
        m_throughputErrorSmoothingFactor = 1 / 100.0;
        m_gainExponent = 200 / 100.0; 
        m_maxSampleError = 15 / 100.0;
        m_currentControlSetting = 0;
        m_totalSamples = 0;
        m_lastThreadCount = 0;
        m_averageThroughputNoise = 0;
        m_elapsedSinceLastChange = 0;
        m_completionsSinceLastChange = 0;
        m_accumulatedCompletionCount = 0;
        m_accumulatedSampleDuration = 0;

        m_samples = new double[m_samplesToMeasure];
        m_threadCounts = new double[m_samplesToMeasure];
    }
    public void set_m_currentControlSetting(int channels){
        m_currentControlSetting = channels;
    }
    public int update(int currentThreadCount, double throughput, int maxConcurrency, double sampleDuration){
        // if (currentThreadCount != m_lastThreadCount){
        //         //ForceChange(currentThreadCount, HillClimbingStateTransition.Initializing);
        // }

        // Update the cumulative stats for this thread count
        m_elapsedSinceLastChange += sampleDuration;
        // m_completionsSinceLastChange += numCompletions;

        
        // Add in any data we've already collected about this sample
        
        sampleDuration += m_accumulatedSampleDuration;
        // numCompletions += m_accumulatedCompletionCount;


        System.out.println("===============================HC3 Start=============================");
        System.out.print("[+] ");
        // We've got enouugh data for our sample; reset our accumulators for next time.
        m_accumulatedSampleDuration = 0;
        m_accumulatedCompletionCount = 0;

        // int sampleIndex = m_totalSamples % m_samplesToMeasure;
        // m_samples[sampleIndex] = throughput;
        // m_threadCounts[sampleIndex] = currentThreadCount;
        // m_totalSamples++;

        Complex threadWaveComponent = new Complex(0.);
        Complex throughputWaveComponent = new Complex(0.);
        double throughputErrorEstimate = 0.;
        Complex ratio = new Complex(0.);
        double confidence = 0;

        HillClimbingStateTransition transition = HillClimbingStateTransition.Warmup;

        // How many samples will we use?  It must be at least the three wave periods we're looking for, and it must also be a whole
        // multiple of the primary wave's period; otherwise the frequency we're looking for will fall between two  frequency bands
        // in the Fourier analysis, and we won't be able to measure it accurately.
        int sampleCount = ((int)Math.min(m_totalSamples-1, m_samplesToMeasure) / m_wavePeriod) * m_wavePeriod;
        if (sampleCount > m_wavePeriod){


            double sampleSum = 0;
            double threadSum = 0;
            for (int i = 0; i < sampleCount; i++)
            {
                System.out.print(", thpt: " + m_samples[(m_totalSamples - sampleCount + i) % m_samplesToMeasure] + ", channel: "+ m_threadCounts[(m_totalSamples - sampleCount + i) % m_samplesToMeasure]);
                sampleSum += m_samples[(m_totalSamples - sampleCount + i) % m_samplesToMeasure];
                threadSum += m_threadCounts[(m_totalSamples - sampleCount + i) % m_samplesToMeasure];
            }
            double averageThroughput = sampleSum / sampleCount;
            double averageThreadCount = threadSum / sampleCount;

            if (averageThroughput > 0 && averageThreadCount > 0){

                // Calculate the periods of the adjacent frequency bands we'll be using to measure noise levels.
                // We want the two adjacent Fourier frequency bands.
                
                double adjacentPeriod1 = sampleCount / (((double)sampleCount / (double)m_wavePeriod) + 1);
                double adjacentPeriod2 = sampleCount / (((double)sampleCount / (double)m_wavePeriod) - 1);

                // Get the the three different frequency components of the throughput (scaled by average
                // throughput).  Our "error" estimate (the amount of noise that might be present in the
                // frequency band we're really interested in) is the average of the adjacent bands.
                
                throughputWaveComponent = getWaveComponent(m_samples, sampleCount, m_wavePeriod).divides(averageThroughput);
                throughputErrorEstimate = getWaveComponent(m_samples, sampleCount, adjacentPeriod1).divides(averageThroughput).abs();
                if (adjacentPeriod2 <= sampleCount){
                    throughputErrorEstimate = Math.max(throughputErrorEstimate, getWaveComponent(m_samples, sampleCount, adjacentPeriod2).divides(averageThroughput).abs());
                }
                
                // Do the same for the thread counts, so we have something to compare to.  We don't measure thread count
                // noise, because there is none; these are exact measurements.
                
                System.out.print(", threadWaveComponent: ");
                threadWaveComponent = getWaveComponent(m_threadCounts, sampleCount, m_wavePeriod).divides(averageThreadCount);
                // Update our movin g average of the throughput noise.  We'll use this later as feedback to
                // determine the new size of the thread wave.
                
                // System.out.print(", m_threadCounts = "+m_threadCounts);
                // System.out.print(", sampleCount = "+sampleCount);
                // System.out.print(", m_wavePeriod = "+m_wavePeriod);
                // System.out.print(", averageThreadCount = "+averageThreadCount);
                if (m_averageThroughputNoise == 0){
                    m_averageThroughputNoise = throughputErrorEstimate;
                }
                else{
                    m_averageThroughputNoise = (m_throughputErrorSmoothingFactor * throughputErrorEstimate) + ((1.0-m_throughputErrorSmoothingFactor) * m_averageThroughputNoise);
                }
                System.out.print(", throughputErrorEstimate = "+throughputErrorEstimate);
                System.out.print(", m_averageThroughputNoise = "+m_averageThroughputNoise);

                if (threadWaveComponent.abs() > 0){
                    // Adjust the throughput wave so it's centered around the target wave, and then calculate the adjusted throughput/thread ratio.
                    ratio = throughputWaveComponent.minus(threadWaveComponent.times(m_targetThroughputRatio)).divides(threadWaveComponent);
                    transition = HillClimbingStateTransition.ClimbingMove;
                }
                else{
                    ratio = new Complex(0.);
                    transition = HillClimbingStateTransition.Stabilizing;
                }
                // Calculate how confident we are in the ratio.  More noise == less confident.  This has
                // the effect of slowing down movements that might be affected by random noise.
                double noiseForConfidence = Math.max(m_averageThroughputNoise, throughputErrorEstimate);
                System.out.print(", threadWaveComponent = "+threadWaveComponent.returnString());
                System.out.print(", threadWaveComponent.abs() = "+threadWaveComponent.abs());
                System.out.print(", noiseForConfidence = "+noiseForConfidence);
                System.out.print(", m_targetSignalToNoiseRatio = "+m_targetSignalToNoiseRatio);
                if (noiseForConfidence > 0){
                    confidence = (threadWaveComponent.abs() / noiseForConfidence) / m_targetSignalToNoiseRatio;
                }
                else{
                    confidence = 1.0; //there is no noise!
                }
            }
        }
        // We use just the real part of the complex ratio we just calculated.  If the throughput signal
        // is exactly in phase with the thread signal, this will be the same as taking the magnitude of
        // the complex move and moving that far up.  If they're 180 degrees out of phase, we'll move
        // backward (because this indicates that our changes are having the opposite of the intended effect).
        // If they're 90 degrees out of phase, we won't move at all, because we can't tell wether we're
        // having a negative or positive effect on throughput.
        
        System.out.print(", ratio = " + ratio.returnString() + ", confidence = " + confidence+ ", transition: "+ transition);
        double move = Math.min(1.0, Math.max(-1.0, ratio.r));
        // System.out.print(", Move1 = "+move);

        
        // Apply our confidence multiplier.
        
        // confidence = 0.1;
        // move *= Math.min(1.0, Math.max(0.0, confidence));
        // System.out.print(", Move2 = "+move);

        
        // Now apply non-linear gain, such that values around zero are attenuated, while higher values
        // are enhanced.  This allows us to move quickly if we're far away from the target, but more slowly
        // if we're getting close, giving us rapid ramp-up without wild oscillations around the target.
        
        // System.out.print(", Move3 = "+move +", sampleCount="+sampleCount);
        double gain = m_maxChangePerSecond * sampleDuration;
        move = Math.pow(Math.abs(move), m_gainExponent) * (move >= 0.0 ? 1 : -1) * gain;
        move = Math.min(move, m_maxChangePerSample);

        System.out.print(", Move4 = "+move + ", gain = " + gain);
        
        // If the result was positive, and CPU is > 95%, refuse the move.
        
        // if (move > 0.0 && ThreadpoolMgr::cpuUtilization > CpuUtilizationHigh){
        //     move = 0.0;
        // }
        
        // Apply the move to our control setting
        
        m_currentControlSetting += move;
        // Calculate the new thread wave magnitude, which is based on the moving average we've been keeping of
        // the throughput error.  This average starts at zero, so we'll start with a nice safe little wave at first.
        

        System.out.print(", m_currentControlSetting = " + m_currentControlSetting );
        int newThreadWaveMagnitude = (int)(0.5 + (m_currentControlSetting * m_averageThroughputNoise * 
                                            m_targetSignalToNoiseRatio * m_threadMagnitudeMultiplier * 2.0));
        System.out.print(", newThreadWaveMagnitude = "+newThreadWaveMagnitude );
        newThreadWaveMagnitude = Math.min(newThreadWaveMagnitude, m_maxThreadWaveMagnitude);
        newThreadWaveMagnitude = Math.max(newThreadWaveMagnitude, 1);
        System.out.print(", newThreadWaveMagnitude = "+newThreadWaveMagnitude );

        
        // Make sure our control setting is within the ThreadPool's limits
        
        // m_currentControlSetting = min(ThreadpoolMgr::MaxLimitTotalWorkerThreads-newThreadWaveMagnitude, m_currentControlSetting);
        // m_currentControlSetting = max(ThreadpoolMgr::MinLimitTotalWorkerThreads, m_currentControlSetting);

        
        // Calculate the new thread count (control setting + square wave)
        
        int newThreadCount = (int)(m_currentControlSetting + newThreadWaveMagnitude * ((m_totalSamples / (m_wavePeriod/2)) % 2));
        newThreadCount = (int)m_currentControlSetting;
        System.out.print(", newThreadCount = "+newThreadCount );
        System.out.print(", ntwm = "+(newThreadWaveMagnitude * ((m_totalSamples / (m_wavePeriod/2)) % 2) ));
        
        // Make sure the new thread count doesn't exceed the ThreadPool's limits
        
        newThreadCount = (int)Math.min(maxConcurrency, newThreadCount);
        newThreadCount = (int)Math.max(1, newThreadCount);
        System.out.print(", newThreadCount = "+newThreadCount );
        System.out.println("");
        System.out.println("===============================HC3=============================");
        return newThreadCount;
    }
    public void addData(double throughput, int currentThreadCount){
        int sampleIndex = m_totalSamples % m_samplesToMeasure;
        m_samples[sampleIndex] = throughput;
        m_threadCounts[sampleIndex] = currentThreadCount;
        m_totalSamples++;
    }

    Complex getWaveComponent(double[] samples, int sampleCount, double period){
        double w = 2.0 * pi / period;
        double cosine = Math.cos(w);
        double sine = Math.sin(w);
        if(sine < 1.2246467991473532E-13){
            sine = 0.0;
        }
        if(cosine < 1.2246467991473532E-13){
            cosine = 0.0;
        }
        double coeff = 2.0 * cosine;
        double q0 = 0, q1 = 0, q2 = 0;
        for (int i = 0; i < sampleCount; i++){
            double sample = samples[(m_totalSamples - sampleCount + i) % m_samplesToMeasure];

            q0 = coeff * q1 - q2 + sample;
            q2 = q1;
            q1 = q0;
        }
        System.out.print(", q1 = "+q1 + ", q2 = " + q2 + ", cosine = "+cosine + ", sine = "+sine);
        System.out.print(", real = "+((q1 - q2 * cosine)/sampleCount) + ", img = " + (q2 * sine/sampleCount));
        return (new Complex(q1 - q2 * cosine, q2 * sine)).divides((double)sampleCount);
    }
}

class Complex {
    double r;   // the real part
    double i;   // the imaginary part

    Complex(double real){
        r = real;
    }
    Complex(int real){
        r = 1.0 * real;
    }
    // create a new object with the given real and imaginary parts
    Complex(double real, double imag) {
        r = real;
        i = imag;
    }
    String returnString(){
        if(this.i < 0){
            return ""+this.r + ""+this.i+"i";
        }else if (this.i == 0.){

            return ""+this.r;
        }else{
            return ""+this.r + "+"+this.i+"i";
        }
        
    }
    // return abs/modulus/magnitude
    double abs() {
        return Math.hypot(r, i);
    }
    Complex plus(Complex b) {
        Complex a = this;             // invoking object
        double real = a.r + b.r;
        double imag = a.i + b.i;
        return new Complex(real, imag);
    }

    // return a new Complex object whose value is (this - b)
    Complex minus(Complex b) {
        Complex a = this;
        double real = a.r - b.r;
        double imag = a.i - b.i;
        return new Complex(real, imag);
    }
    Complex times(Complex b) {
        Complex a = this;
        double real = a.r * b.r - a.i * b.i;
        double imag = a.r * b.i + a.i * b.r;
        return new Complex(real, imag);
    }
    Complex times(double b) {
        Complex a = this;
        double real = a.i * b;
        double imag = a.r * b;
        return new Complex(real, imag);
    }
    Complex reciprocal() {
        double scale = r*r + i*i;
        return new Complex(r / scale, -i / scale);
    }
    Complex divides(Complex b) {
        Complex a = this;
        return a.times(b.reciprocal());
    }
    Complex divides(double b) {
        Complex a = this;
        return new Complex(a.r/b, a.i/b);
    }
}


